import { randomUUID } from "node:crypto";

import type { Pool } from "pg";

import type {
  RefreshLease,
  RotateSessionInput,
} from "./session-contracts.ts";

export class PostgresRefreshStore {
  constructor(private readonly pool: Pool) {}

  async acquire(sessionTokenHash: Buffer, now: Date): Promise<RefreshLease | null> {
    const leaseId = randomUUID();
    const result = await this.pool.query<{
      id: string;
      provider_subject: string;
      refresh_token_ciphertext: Buffer;
      session_version: string;
      token_key_id: string;
    }>(
      `UPDATE web_auth_spike.sessions
       SET refresh_lease_id = $2,
           refresh_lease_version = session_version,
           refresh_lease_expires_at = $3 + interval '15 seconds',
           refresh_attempted_at = $3,
           updated_at = $3
       WHERE session_token_hash = $1
         AND revoked_at IS NULL
         AND session_expires_at > $3
         AND access_token_expires_at <= $3 + interval '30 seconds'
         AND refresh_token_ciphertext IS NOT NULL
         AND (refresh_retry_after IS NULL OR refresh_retry_after <= $3)
         AND (refresh_lease_id IS NULL OR refresh_lease_expires_at <= $3)
       RETURNING id, provider_subject, refresh_token_ciphertext, session_version, token_key_id`,
      [sessionTokenHash, leaseId, now],
    );
    const row = result.rows[0];
    if (!row) return null;
    return {
      leaseId,
      refreshTokenCiphertext: row.refresh_token_ciphertext,
      sessionId: row.id,
      sessionVersion: Number(row.session_version),
      subject: row.provider_subject,
      tokenKeyId: row.token_key_id,
    };
  }

  async rotate(input: RotateSessionInput): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE web_auth_spike.sessions
       SET access_token_ciphertext = $4,
           refresh_token_ciphertext = COALESCE($5, refresh_token_ciphertext),
           id_token_ciphertext = COALESCE($6, id_token_ciphertext),
           token_key_id = $7,
           access_token_expires_at = $8,
           session_version = session_version + 1,
           refresh_version = refresh_version + 1,
           refresh_lease_id = NULL,
           refresh_lease_version = NULL,
           refresh_lease_expires_at = NULL,
           refresh_retry_after = NULL,
           updated_at = now()
       WHERE id = $1
         AND session_version = $2
         AND refresh_lease_id = $3
         AND refresh_lease_version = $2
         AND revoked_at IS NULL`,
      [
        input.sessionId,
        input.expectedSessionVersion,
        input.leaseId,
        input.accessToken.ciphertext,
        input.refreshToken?.ciphertext ?? null,
        input.idToken?.ciphertext ?? null,
        input.accessToken.keyId,
        input.accessTokenExpiresAt,
      ],
    );
    return result.rowCount === 1;
  }

  async finishTransientFailure(lease: RefreshLease, retryAfter: Date): Promise<void> {
    await this.pool.query(
      `UPDATE web_auth_spike.sessions
       SET session_version = session_version + 1,
           refresh_lease_id = NULL,
           refresh_lease_version = NULL,
           refresh_lease_expires_at = NULL,
           refresh_retry_after = $4,
           updated_at = now()
       WHERE id = $1 AND session_version = $2 AND refresh_lease_id = $3`,
      [lease.sessionId, lease.sessionVersion, lease.leaseId, retryAfter],
    );
  }

  async revokeLease(lease: RefreshLease, now: Date): Promise<void> {
    await this.pool.query(
      `UPDATE web_auth_spike.sessions
       SET revoked_at = $4,
           session_version = session_version + 1,
           refresh_lease_id = NULL,
           refresh_lease_version = NULL,
           refresh_lease_expires_at = NULL,
           updated_at = $4
       WHERE id = $1 AND session_version = $2 AND refresh_lease_id = $3`,
      [lease.sessionId, lease.sessionVersion, lease.leaseId, now],
    );
  }
}
