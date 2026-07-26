import "server-only";

import { randomUUID } from "node:crypto";

import type { Pool } from "pg";

import type {
  RefreshLease,
  RotateSessionInput
} from "@/server/auth/session-contracts";

export const REFRESH_LEASE_SECONDS = 15;

export class PostgresRefreshStore {
  constructor(private readonly pool: Pool) {}

  async acquire(sessionTokenHash: Buffer, now: Date): Promise<RefreshLease | null> {
    const leaseId = randomUUID();
    const result = await this.pool.query<{
      id: string;
      id_token_ciphertext: Buffer | null;
      provider_subject: string;
      refresh_token_ciphertext: Buffer;
      session_token_hash: Buffer;
      session_version: string;
      token_key_id: string;
    }>(
      `UPDATE agriinsight_web.sessions
       SET refresh_lease_id = $2,
           refresh_lease_version = session_version,
           refresh_lease_expires_at = $3 + make_interval(secs => $4),
           refresh_attempted_at = $3,
           updated_at = $3
       WHERE session_token_hash = $1
         AND revoked_at IS NULL
         AND session_expires_at > $3
         AND access_token_expires_at <= $3 + interval '30 seconds'
         AND refresh_token_ciphertext IS NOT NULL
         AND (refresh_retry_after IS NULL OR refresh_retry_after <= $3)
         AND (refresh_lease_id IS NULL OR refresh_lease_expires_at <= $3)
       RETURNING id, session_token_hash, provider_subject,
                 refresh_token_ciphertext, id_token_ciphertext,
                 session_version, token_key_id`,
      [sessionTokenHash, leaseId, now, REFRESH_LEASE_SECONDS]
    );
    const row = result.rows[0];
    if (!row) return null;
    return {
      idTokenCiphertext: row.id_token_ciphertext,
      leaseId,
      refreshTokenCiphertext: row.refresh_token_ciphertext,
      sessionId: row.id,
      sessionTokenHash: row.session_token_hash,
      sessionVersion: Number(row.session_version),
      subject: row.provider_subject,
      tokenKeyId: row.token_key_id
    };
  }

  async rotate(input: RotateSessionInput): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE agriinsight_web.sessions
       SET access_token_ciphertext = $4,
           refresh_token_ciphertext = $5,
           id_token_ciphertext = $6,
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
        input.refreshToken.ciphertext,
        input.idToken?.ciphertext ?? null,
        input.accessToken.keyId,
        input.accessTokenExpiresAt
      ]
    );
    return result.rowCount === 1;
  }

  async finishTransientFailure(
    lease: RefreshLease,
    retryAfter: Date
  ): Promise<void> {
    await this.pool.query(
      `UPDATE agriinsight_web.sessions
       SET session_version = session_version + 1,
           refresh_lease_id = NULL,
           refresh_lease_version = NULL,
           refresh_lease_expires_at = NULL,
           refresh_retry_after = $4,
           updated_at = now()
       WHERE id = $1 AND session_version = $2 AND refresh_lease_id = $3`,
      [lease.sessionId, lease.sessionVersion, lease.leaseId, retryAfter]
    );
  }

  async revokeLease(lease: RefreshLease, now: Date): Promise<void> {
    await this.pool.query(
      `UPDATE agriinsight_web.sessions
       SET revoked_at = $4,
           session_version = session_version + 1,
           refresh_lease_id = NULL,
           refresh_lease_version = NULL,
           refresh_lease_expires_at = NULL,
           updated_at = $4
       WHERE id = $1 AND session_version = $2 AND refresh_lease_id = $3`,
      [lease.sessionId, lease.sessionVersion, lease.leaseId, now]
    );
  }
}
