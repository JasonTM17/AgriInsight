import "server-only";

import { randomUUID } from "node:crypto";

import type { Pool } from "pg";

import { PostgresRefreshStore } from "@/server/auth/postgres-refresh-store";
import {
  mapSession,
  type SessionRow
} from "@/server/auth/postgres-session-row";
import type {
  ConsumedPreauth,
  CreatePreauthInput,
  CreateSessionInput,
  RefreshLease,
  RotateSessionInput,
  SessionStore,
  StoredSession
} from "@/server/auth/session-contracts";

export class PostgresSessionStore implements SessionStore {
  private schemaValidation?: Promise<void>;
  private readonly refresh: PostgresRefreshStore;

  constructor(private readonly pool: Pool) {
    this.refresh = new PostgresRefreshStore(pool);
  }

  async createPreauth(input: CreatePreauthInput): Promise<void> {
    await this.ensureSchema();
    const inserted = await this.pool.query(
      `WITH preauth_lock AS MATERIALIZED (
         SELECT pg_advisory_xact_lock(198874937)
       ),
       cleanup AS (
         DELETE FROM agriinsight_web.preauth_requests
         WHERE expires_at <= now()
            OR consumed_at < now() - interval '5 minutes'
       ),
       session_cleanup AS (
         DELETE FROM agriinsight_web.sessions
         WHERE session_expires_at < now() - interval '30 days'
            OR revoked_at < now() - interval '30 days'
       ),
       capacity AS (
         SELECT count(*) AS active_count
         FROM agriinsight_web.preauth_requests, preauth_lock
         WHERE consumed_at IS NULL AND expires_at > now()
       )
       INSERT INTO agriinsight_web.preauth_requests (
         id, state_hash, browser_binding_hash, pkce_verifier_ciphertext,
         nonce_ciphertext, token_key_id, return_path, expires_at
       )
       SELECT $1, $2, $3, $4, $5, $6, $7, $8
       FROM capacity
       WHERE active_count < 10000`,
      [
        randomUUID(),
        input.stateHash,
        input.browserBindingHash,
        input.pkceVerifier.ciphertext,
        input.nonce.ciphertext,
        input.nonce.keyId,
        input.returnPath,
        input.expiresAt
      ]
    );
    if (inserted.rowCount !== 1) {
      throw new Error("Pre-authentication request capacity is exhausted");
    }
  }

  async consumePreauth(
    stateHash: Buffer,
    bindingHash: Buffer,
    now: Date
  ): Promise<ConsumedPreauth | null> {
    await this.ensureSchema();
    const result = await this.pool.query<{
      nonce_ciphertext: Buffer;
      pkce_verifier_ciphertext: Buffer;
      return_path: string;
      token_key_id: string;
    }>(
      `UPDATE agriinsight_web.preauth_requests
       SET consumed_at = $3
       WHERE state_hash = $1
         AND browser_binding_hash = $2
         AND consumed_at IS NULL
         AND expires_at > $3
       RETURNING nonce_ciphertext, pkce_verifier_ciphertext, return_path, token_key_id`,
      [stateHash, bindingHash, now]
    );
    const row = result.rows[0];
    return row
      ? {
          nonceCiphertext: row.nonce_ciphertext,
          pkceVerifierCiphertext: row.pkce_verifier_ciphertext,
          returnPath: row.return_path,
          tokenKeyId: row.token_key_id
        }
      : null;
  }

  async createSession(input: CreateSessionInput): Promise<void> {
    await this.ensureSchema();
    await this.pool.query(
      `INSERT INTO agriinsight_web.sessions (
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
        input.sessionExpiresAt
      ]
    );
  }

  async findSession(sessionTokenHash: Buffer): Promise<StoredSession | null> {
    await this.ensureSchema();
    const result = await this.pool.query<SessionRow>(
      `SELECT id, session_token_hash, provider_issuer, provider_subject, token_key_id,
              access_token_ciphertext, refresh_token_ciphertext, id_token_ciphertext,
              access_token_expires_at, session_expires_at, session_version,
              refresh_lease_id, refresh_lease_expires_at, revoked_at
       FROM agriinsight_web.sessions
       WHERE session_token_hash = $1`,
      [sessionTokenHash]
    );
    return result.rows[0] ? mapSession(result.rows[0]) : null;
  }

  async acquireRefreshLease(
    sessionTokenHash: Buffer,
    now: Date
  ): Promise<RefreshLease | null> {
    await this.ensureSchema();
    return this.refresh.acquire(sessionTokenHash, now);
  }

  async rotateSession(input: RotateSessionInput): Promise<boolean> {
    await this.ensureSchema();
    return this.refresh.rotate(input);
  }

  async finishTransientRefreshFailure(
    lease: RefreshLease,
    retryAfter: Date
  ): Promise<void> {
    await this.ensureSchema();
    await this.refresh.finishTransientFailure(lease, retryAfter);
  }

  async revokeRefreshLease(lease: RefreshLease, now: Date): Promise<void> {
    await this.ensureSchema();
    await this.refresh.revokeLease(lease, now);
  }

  async revokeSession(
    sessionTokenHash: Buffer,
    now: Date
  ): Promise<StoredSession | null> {
    await this.ensureSchema();
    const result = await this.pool.query<SessionRow>(
      `UPDATE agriinsight_web.sessions
       SET revoked_at = COALESCE(revoked_at, $2),
           session_version = CASE
             WHEN revoked_at IS NULL THEN session_version + 1
             ELSE session_version
           END,
           refresh_lease_id = NULL,
           refresh_lease_version = NULL,
           refresh_lease_expires_at = NULL,
           updated_at = $2
       WHERE session_token_hash = $1
       RETURNING id, session_token_hash, provider_issuer, provider_subject, token_key_id,
                 access_token_ciphertext, refresh_token_ciphertext, id_token_ciphertext,
                 access_token_expires_at, session_expires_at, session_version,
                 refresh_lease_id, refresh_lease_expires_at, revoked_at`,
      [sessionTokenHash, now]
    );
    return result.rows[0] ? mapSession(result.rows[0]) : null;
  }

  private ensureSchema(): Promise<void> {
    if (this.schemaValidation) return this.schemaValidation;
    const pending = this.pool
      .query<{ version: number }>(
        "SELECT max(version)::integer AS version FROM agriinsight_web.schema_migrations"
      )
      .then((result) => {
        if (result.rows[0]?.version !== 1) {
          throw new Error("Web session schema version 1 is required");
        }
      });
    const retriable = pending.catch((error: unknown) => {
      if (this.schemaValidation === retriable) this.schemaValidation = undefined;
      throw error;
    });
    this.schemaValidation = retriable;
    return this.schemaValidation;
  }
}
