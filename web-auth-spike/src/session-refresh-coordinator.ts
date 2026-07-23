import { AuthError, invalidSessionError, sanitizedAuthError } from "./auth-errors.ts";
import { ProviderRefreshError, type OidcProviderAdapter } from "./provider-adapter.ts";
import type {
  EncryptedValue,
  RefreshLease,
  SessionStore,
} from "./session-contracts.ts";
import { TokenCipher } from "./token-crypto.ts";

export type ValidSession = Readonly<{
  accessToken: string;
  expiresAt: Date;
  sessionVersion: number;
  subject: string;
}>;

export async function refreshLeasedSession(
  lease: RefreshLease,
  now: Date,
  store: SessionStore,
  cipher: TokenCipher,
  provider: OidcProviderAdapter,
): Promise<ValidSession> {
  if (lease.tokenKeyId !== cipher.keyId) throw invalidSessionError();
  try {
    const refreshToken = cipher.open(lease.refreshTokenCiphertext, "session:refresh");
    const tokens = await provider.refresh(refreshToken);
    const rotated = await store.rotateSession({
      accessToken: encrypt(cipher, tokens.accessToken, "session:access"),
      accessTokenExpiresAt: tokens.accessTokenExpiresAt,
      expectedSessionVersion: lease.sessionVersion,
      idToken: tokens.idToken ? encrypt(cipher, tokens.idToken, "session:id") : undefined,
      leaseId: lease.leaseId,
      refreshToken: tokens.refreshToken
        ? encrypt(cipher, tokens.refreshToken, "session:refresh")
        : undefined,
      sessionId: lease.sessionId,
    });
    if (!rotated) {
      try {
        await provider.bestEffortRevoke(tokens.refreshToken ?? refreshToken);
      } catch {
        // Local revocation won the race; provider cleanup remains best effort.
      }
      throw invalidSessionError();
    }
    return {
      accessToken: tokens.accessToken,
      expiresAt: tokens.accessTokenExpiresAt,
      sessionVersion: lease.sessionVersion + 1,
      subject: lease.subject,
    };
  } catch (error) {
    if (error instanceof AuthError) throw error;
    if (error instanceof ProviderRefreshError && error.invalidGrant) {
      await store.revokeRefreshLease(lease, now);
      throw invalidSessionError();
    }
    await store.finishTransientRefreshFailure(lease, new Date(now.getTime() + 2000));
    throw sanitizedAuthError(error);
  }
}

function encrypt(cipher: TokenCipher, value: string, purpose: string): EncryptedValue {
  return { ciphertext: cipher.seal(value, purpose), keyId: cipher.keyId };
}
