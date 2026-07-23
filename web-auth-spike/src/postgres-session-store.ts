import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { randomUUID } from "node:crypto";

import type { Pool, QueryResultRow } from "pg";

import { PostgresRefreshStore } from "./postgres-refresh-store.ts";
import type {
  ConsumedPreauth,
  CreatePreauthInput,
  CreateSessionInput,
  RefreshLease,
  RotateSessionInput,
  SessionStore,
  StoredSession,
} from "./session-contracts.ts";

type SessionRow = QueryResultRow & {
  access_token_ciphertext: Buffer;
  access_token_expires_at: Date;
  id: string;
  id_token_ciphertext: Buffer | null;
  provider_issuer: string;
  provider_subject: string;
  refresh_lease_expires_at: Date | null;
  refresh_lease_id: string | null;
  refresh_token_ciphertext: Buffer | null;
  revoked_at: Date | null;
  session_expires_at: Date;
  session_version: string;
  token_key_id: string;
};

export class PostgresSessionStore implements SessionStore {
  private migration?: Promise<void>;
  private readonly refresh: PostgresRefreshStore;

  constructor(private readonly pool: Pool) {
    this.refresh = new PostgresRefreshStore(pool);
  }

  migrate(): Promise<void> {
    this.migration ??= readFile(
      join(process.cwd(), "migrations", "001-auth-spike.sql"),
      "utf8",
    ).then(async (sql) => {
      await this.pool.query(sql);
    });
    return this.migration;
  }

  async createPreauth(input: CreatePreauthInput): Promise<void> {
    await this.migrate();
    await this.pool.query(
      `INSERT INTO web_auth_spike.preauth_requests (
         id, state_hash, browser_binding_hash, pkce_verifier_ciphertext,
         nonce_ciphertext, token_key_id, return_path, expires_at
       ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [
        randomUUID(),
        input.stateHash,
        input.browserBindingHash,
        input.pkceVerifier.ciphertext,
        input.nonce.ciphertext,
        input.nonce.keyId,
        input.returnPath,
        input.expiresAt,
      ],
    );
  }

  async consumePreauth(
    stateHash: Buffer,
    bindingHash: Buffer,
    now: Date,
  ): Promise<ConsumedPreauth | null> {
    await this.migrate();
    const result = await this.pool.query<{
      nonce_ciphertext: Buffer;
      pkce_verifier_ciphertext: Buffer;
      return_path: string;
      token_key_id: string;
    }>(
      `UPDATE web_auth_spike.preauth_requests
       SET consumed_at = $3
       WHERE state_hash = $1
         AND browser_binding_hash = $2
         AND consumed_at IS NULL
         AND expires_at > $3
       RETURNING nonce_ciphertext, pkce_verifier_ciphertext, return_path, token_key_id`,
      [stateHash, bindingHash, now],
    );
    const row = result.rows[0];
    if (!row) return null;
    return {
      nonceCiphertext: row.nonce_ciphertext,
      pkceVerifierCiphertext: row.pkce_verifier_ciphertext,
      returnPath: row.return_path,
      tokenKeyId: row.token_key_id,
    };
  }

  async createSession(input: CreateSessionInput): Promise<void> {
    await this.migrate();
    await this.pool.query(
      `INSERT INTO web_auth_spike.sessions (
         id, session_token_hash, provider_issuer, provider_subject, token_key_id,
         access_token_ciphertext, refresh_token_ciphertext, id_token_ciphertext,
         access_token_expires_at, session_expires_at
       ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
      [
        randomUUID(),
        input.sessionTokenHash,
        input.issuer,
        input.subject,
        input.accessToken.keyId,
        input.accessToken.ciphertext,
        input.refreshToken?.ciphertext ?? null,
        input.idToken?.ciphertext ?? null,
        input.accessTokenExpiresAt,
        input.sessionExpiresAt,
      ],
    );
  }

  async findSession(sessionTokenHash: Buffer): Promise<StoredSession | null> {
    await this.migrate();
    const result = await this.pool.query<SessionRow>(
      `SELECT id, provider_issuer, provider_subject, token_key_id,
              access_token_ciphertext, refresh_token_ciphertext, id_token_ciphertext,
              access_token_expires_at, session_expires_at, session_version,
              refresh_lease_id, refresh_lease_expires_at, revoked_at
       FROM web_auth_spike.sessions
       WHERE session_token_hash = $1`,
      [sessionTokenHash],
    );
    return result.rows[0] ? mapSession(result.rows[0]) : null;
  }

  async acquireRefreshLease(
    sessionTokenHash: Buffer,
    now: Date,
  ): Promise<RefreshLease | null> {
    await this.migrate();
    return this.refresh.acquire(sessionTokenHash, now);
  }

  async rotateSession(input: RotateSessionInput): Promise<boolean> {
    await this.migrate();
    return this.refresh.rotate(input);
  }

  async finishTransientRefreshFailure(lease: RefreshLease, retryAfter: Date): Promise<void> {
    await this.migrate();
    await this.refresh.finishTransientFailure(lease, retryAfter);
  }

  async revokeRefreshLease(lease: RefreshLease, now: Date): Promise<void> {
    await this.migrate();
    await this.refresh.revokeLease(lease, now);
  }

  async revokeSession(
    sessionTokenHash: Buffer,
    now: Date,
  ): Promise<StoredSession | null> {
    await this.migrate();
    const result = await this.pool.query<SessionRow>(
      `UPDATE web_auth_spike.sessions
       SET revoked_at = COALESCE(revoked_at, $2),
           session_version = CASE WHEN revoked_at IS NULL THEN session_version + 1 ELSE session_version END,
           refresh_lease_id = NULL,
           refresh_lease_version = NULL,
           refresh_lease_expires_at = NULL,
           updated_at = $2
       WHERE session_token_hash = $1
       RETURNING id, provider_issuer, provider_subject, token_key_id,
                 access_token_ciphertext, refresh_token_ciphertext, id_token_ciphertext,
                 access_token_expires_at, session_expires_at, session_version,
                 refresh_lease_id, refresh_lease_expires_at, revoked_at`,
      [sessionTokenHash, now],
    );
    return result.rows[0] ? mapSession(result.rows[0]) : null;
  }
}

function mapSession(row: SessionRow): StoredSession {
  return {
    accessTokenCiphertext: row.access_token_ciphertext,
    accessTokenExpiresAt: row.access_token_expires_at,
    id: row.id,
    idTokenCiphertext: row.id_token_ciphertext,
    issuer: row.provider_issuer,
    refreshLeaseExpiresAt: row.refresh_lease_expires_at,
    refreshLeaseId: row.refresh_lease_id,
    refreshTokenCiphertext: row.refresh_token_ciphertext,
    revokedAt: row.revoked_at,
    sessionExpiresAt: row.session_expires_at,
    sessionVersion: Number(row.session_version),
    subject: row.provider_subject,
    tokenKeyId: row.token_key_id,
  };
}
