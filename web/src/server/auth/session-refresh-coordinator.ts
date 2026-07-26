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
import { sessionTokenPurpose } from "@/server/auth/auth-session-state";
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
  if (!cipher.canOpen(lease.tokenKeyId)) {
    await store.revokeRefreshLease(lease, now);
    throw invalidSessionError();
  }

  try {
    const refreshToken = cipher.openWithKeyId(
      lease.tokenKeyId,
      lease.refreshTokenCiphertext,
      sessionTokenPurpose(lease.sessionTokenHash, "refresh")
    );
    const priorIdToken = lease.idTokenCiphertext
      ? cipher.openWithKeyId(
          lease.tokenKeyId,
          lease.idTokenCiphertext,
          sessionTokenPurpose(lease.sessionTokenHash, "id")
        )
      : undefined;
    const refreshed = await provider.refresh(refreshToken);
    const rotated = await store.rotateSession({
      accessToken: {
        ciphertext: cipher.seal(
          refreshed.accessToken,
          sessionTokenPurpose(lease.sessionTokenHash, "access")
        ),
        keyId: cipher.keyId
      },
      accessTokenExpiresAt: refreshed.accessTokenExpiresAt,
      expectedSessionVersion: lease.sessionVersion,
      idToken: refreshed.idToken ?? priorIdToken
        ? {
            ciphertext: cipher.seal(
              refreshed.idToken ?? priorIdToken!,
              sessionTokenPurpose(lease.sessionTokenHash, "id")
            ),
            keyId: cipher.keyId
          }
        : undefined,
      leaseId: lease.leaseId,
      refreshToken: {
        ciphertext: cipher.seal(
          refreshed.refreshToken ?? refreshToken,
          sessionTokenPurpose(lease.sessionTokenHash, "refresh")
        ),
        keyId: cipher.keyId
      },
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
