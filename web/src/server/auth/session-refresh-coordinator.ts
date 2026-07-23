import "server-only";

import { AuthError, invalidSessionError } from "@/server/auth/auth-error";
import {
  ProviderRefreshError,
  type OidcProviderAdapter
} from "@/server/auth/provider";
import type {
  RefreshLease,
  SessionStore
} from "@/server/auth/session-contracts";
import { TokenCipher } from "@/server/auth/token-crypto";

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
  provider: OidcProviderAdapter
): Promise<ValidSession> {
  if (lease.tokenKeyId !== cipher.keyId) {
    await store.revokeRefreshLease(lease, now);
    throw invalidSessionError();
  }

  try {
    const refreshToken = cipher.open(
      lease.refreshTokenCiphertext,
      "session:refresh"
    );
    const refreshed = await provider.refresh(refreshToken);
    const rotated = await store.rotateSession({
      accessToken: {
        ciphertext: cipher.seal(refreshed.accessToken, "session:access"),
        keyId: cipher.keyId
      },
      accessTokenExpiresAt: refreshed.accessTokenExpiresAt,
      expectedSessionVersion: lease.sessionVersion,
      idToken: refreshed.idToken
        ? {
            ciphertext: cipher.seal(refreshed.idToken, "session:id"),
            keyId: cipher.keyId
          }
        : undefined,
      leaseId: lease.leaseId,
      refreshToken: refreshed.refreshToken
        ? {
            ciphertext: cipher.seal(refreshed.refreshToken, "session:refresh"),
            keyId: cipher.keyId
          }
        : undefined,
      sessionId: lease.sessionId
    });
    if (!rotated) throw invalidSessionError();
    return {
      accessToken: refreshed.accessToken,
      expiresAt: refreshed.accessTokenExpiresAt,
      sessionVersion: lease.sessionVersion + 1,
      subject: lease.subject
    };
  } catch (error) {
    if (error instanceof ProviderRefreshError && error.invalidGrant) {
      await store.revokeRefreshLease(lease, now);
      throw invalidSessionError();
    }
    await store.finishTransientRefreshFailure(
      lease,
      new Date(now.getTime() + 5_000)
    );
    throw new AuthError(
      "issuer_unavailable",
      503,
      "Không thể làm mới phiên đăng nhập lúc này.",
      { cause: error }
    );
  }
}
