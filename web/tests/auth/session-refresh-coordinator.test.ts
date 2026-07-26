import { describe, expect, it } from "vitest";

import type { OidcProviderAdapter } from "@/server/auth/provider";
import type {
  RefreshLease,
  RotateSessionInput,
  SessionStore
} from "@/server/auth/session-contracts";
import {
  refreshLeasedSession
} from "@/server/auth/session-refresh-coordinator";
import { sessionTokenPurpose } from "@/server/auth/auth-session-state";
import { TokenCipher } from "@/server/auth/token-crypto";
import { PROVIDER_HTTP_TIMEOUT_SECONDS } from "@/server/auth/openid-client-provider";
import { REFRESH_LEASE_SECONDS } from "@/server/auth/postgres-refresh-store";

describe("session refresh coordinator", () => {
  it("keeps the database lease beyond twice the provider timeout", () => {
    expect(REFRESH_LEASE_SECONDS).toBeGreaterThan(
      PROVIDER_HTTP_TIMEOUT_SECONDS * 2
    );
  });

  it("re-encrypts retained refresh and ID tokens with the current key", async () => {
    const sessionTokenHash = Buffer.alloc(32, 9);
    const oldKey = Buffer.alloc(32, 1);
    const currentKey = Buffer.alloc(32, 2);
    const oldCipher = new TokenCipher("old-v1", oldKey);
    const cipher = new TokenCipher(
      "current-v2",
      currentKey,
      new Map([["old-v1", oldKey]])
    );
    const lease: RefreshLease = {
      idTokenCiphertext: oldCipher.seal(
        "old-id-token",
        sessionTokenPurpose(sessionTokenHash, "id")
      ),
      leaseId: "lease-1",
      refreshTokenCiphertext: oldCipher.seal(
        "old-refresh-token",
        sessionTokenPurpose(sessionTokenHash, "refresh")
      ),
      sessionId: "session-1",
      sessionTokenHash,
      sessionVersion: 4,
      subject: "subject-1",
      tokenKeyId: "old-v1"
    };
    let rotation: RotateSessionInput | undefined;
    const store = {
      async rotateSession(input: RotateSessionInput) {
        rotation = input;
        return true;
      }
    } as SessionStore;
    const provider = {
      async refresh(refreshToken: string) {
        expect(refreshToken).toBe("old-refresh-token");
        return {
          accessToken: "new-access-token",
          accessTokenExpiresAt: new Date("2026-07-24T00:00:00Z")
        };
      }
    } as OidcProviderAdapter;

    const session = await refreshLeasedSession(
      lease,
      new Date("2026-07-23T12:00:00Z"),
      store,
      cipher,
      provider
    );

    expect(session.sessionVersion).toBe(5);
    expect(rotation?.accessToken.keyId).toBe("current-v2");
    expect(rotation?.refreshToken.keyId).toBe("current-v2");
    expect(rotation?.idToken?.keyId).toBe("current-v2");
    expect(
      cipher.open(
        rotation!.refreshToken.ciphertext,
        sessionTokenPurpose(sessionTokenHash, "refresh")
      )
    ).toBe("old-refresh-token");
    expect(
      cipher.open(
        rotation!.idToken!.ciphertext,
        sessionTokenPurpose(sessionTokenHash, "id")
      )
    ).toBe("old-id-token");
  });

  it("rejects ciphertext transplanted to a different session binding", () => {
    const cipher = new TokenCipher("current-v1", Buffer.alloc(32, 7));
    const victimHash = Buffer.alloc(32, 1);
    const attackerHash = Buffer.alloc(32, 2);
    const victimToken = cipher.seal(
      "victim-access-token",
      sessionTokenPurpose(victimHash, "access")
    );

    expect(() =>
      cipher.open(
        victimToken,
        sessionTokenPurpose(attackerHash, "access")
      )
    ).toThrow();
  });
});
