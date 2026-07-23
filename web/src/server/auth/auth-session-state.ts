import "server-only";

import { invalidSessionError } from "@/server/auth/auth-error";
import type {
  CreateSessionInput,
  EncryptedValue,
  ProviderTokens,
  SessionStore,
  StoredSession
} from "@/server/auth/session-contracts";
import type { ValidSession } from "@/server/auth/session-refresh-coordinator";
import { TokenCipher, hashOpaqueToken } from "@/server/auth/token-crypto";
import type { WebEnvironment } from "@/server/config/environment";

export function encryptedValue(
  cipher: TokenCipher,
  value: string,
  purpose: string
): EncryptedValue {
  return {
    ciphertext: cipher.seal(value, purpose),
    keyId: cipher.keyId
  };
}

export function createSessionInput(
  env: WebEnvironment,
  cipher: TokenCipher,
  tokens: ProviderTokens,
  sessionToken: string,
  now: Date
): CreateSessionInput {
  return {
    accessToken: encryptedValue(cipher, tokens.accessToken, "session:access"),
    accessTokenExpiresAt: tokens.accessTokenExpiresAt,
    idToken: tokens.idToken
      ? encryptedValue(cipher, tokens.idToken, "session:id")
      : undefined,
    issuer: env.issuer.href.replace(/\/$/, ""),
    refreshToken: tokens.refreshToken
      ? encryptedValue(cipher, tokens.refreshToken, "session:refresh")
      : undefined,
    sessionExpiresAt: new Date(
      now.getTime() + env.sessionLifetimeSeconds * 1000
    ),
    sessionTokenHash: hashOpaqueToken(sessionToken),
    subject: tokens.subject
  };
}

export function toValidSession(
  cipher: TokenCipher,
  session: StoredSession
): ValidSession {
  assertCurrentKey(cipher, session.tokenKeyId);
  return {
    accessToken: cipher.open(session.accessTokenCiphertext, "session:access"),
    expiresAt: session.accessTokenExpiresAt,
    sessionVersion: session.sessionVersion,
    subject: session.subject
  };
}

export function assertCurrentKey(cipher: TokenCipher, keyId: string): void {
  if (keyId !== cipher.keyId) throw invalidSessionError();
}

export async function requireUsableSession(
  env: WebEnvironment,
  store: SessionStore,
  tokenHash: Buffer,
  session: StoredSession | null,
  now: Date
): Promise<StoredSession> {
  if (!session || session.revokedAt || session.sessionExpiresAt <= now) {
    throw invalidSessionError();
  }
  if (session.issuer !== env.issuer.href.replace(/\/$/, "")) {
    await store.revokeSession(tokenHash, now);
    throw invalidSessionError();
  }
  return session;
}
