import type { QueryResultRow } from "pg";

import type { StoredSession } from "@/server/auth/session-contracts";

export type SessionRow = QueryResultRow & {
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

export function mapSession(row: SessionRow): StoredSession {
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
    tokenKeyId: row.token_key_id
  };
}
