import "server-only";

import { setTimeout as delay } from "node:timers/promises";

import { AuthError, invalidSessionError } from "@/server/auth/auth-error";
import type { OidcProviderAdapter } from "@/server/auth/provider";
import { allowlistedReturnPath } from "@/server/auth/request-policy";
import type { SessionStore, StoredSession } from "@/server/auth/session-contracts";
import {
  assertCurrentKey,
  createSessionInput,
  encryptedValue,
  requireUsableSession,
  toValidSession
} from "@/server/auth/auth-session-state";
import {
  refreshLeasedSession,
  type ValidSession
} from "@/server/auth/session-refresh-coordinator";
import { TokenCipher, hashOpaqueToken, randomOpaqueToken } from "@/server/auth/token-crypto";
import type { WebEnvironment } from "@/server/config/environment";

const PREAUTH_LIFETIME_MS = 5 * 60 * 1000;
const REFRESH_SKEW_MS = 30 * 1000;
const REFRESH_WAIT_ATTEMPTS = 120;
const REFRESH_POLL_INTERVAL_MS = 50;

export type LoginStart = Readonly<{
  browserBinding: string;
  redirectUrl: URL;
}>;

export type CallbackResult = Readonly<{
  returnPath: string;
  sessionToken: string;
}>;

export class AuthService {
  constructor(
    private readonly env: WebEnvironment,
    private readonly store: SessionStore,
    private readonly cipher: TokenCipher,
    private readonly provider: OidcProviderAdapter
  ) {}

  async beginLogin(
    candidateReturnPath?: string | null,
    now = new Date()
  ): Promise<LoginStart> {
    const browserBinding = randomOpaqueToken();
    const nonce = randomOpaqueToken();
    const state = randomOpaqueToken();
    const verifier = this.provider.createPkceVerifier();
    const codeChallenge = await this.provider.calculatePkceChallenge(verifier);
    await this.store.createPreauth({
      browserBindingHash: hashOpaqueToken(browserBinding),
      expiresAt: new Date(now.getTime() + PREAUTH_LIFETIME_MS),
      nonce: this.encrypt(nonce, "preauth:nonce"),
      pkceVerifier: this.encrypt(verifier, "preauth:pkce"),
      returnPath: allowlistedReturnPath(candidateReturnPath),
      stateHash: hashOpaqueToken(state)
    });
    return {
      browserBinding,
      redirectUrl: await this.provider.buildAuthorizationRedirect({
        codeChallenge,
        nonce,
        state
      })
    };
  }

  async completeCallback(
    callbackUrl: URL,
    browserBinding: string | undefined,
    now = new Date()
  ): Promise<CallbackResult> {
    const state = callbackUrl.searchParams.get("state");
    const code = callbackUrl.searchParams.get("code");
    if (!state || !code || !browserBinding || callbackUrl.searchParams.has("error")) {
      throw new AuthError(
        "invalid_request",
        400,
        "Phản hồi đăng nhập không đầy đủ."
      );
    }
    const preauth = await this.store.consumePreauth(
      hashOpaqueToken(state),
      hashOpaqueToken(browserBinding),
      now
    );
    if (!preauth) {
      throw new AuthError(
        "invalid_state",
        400,
        "Trạng thái đăng nhập không hợp lệ hoặc đã hết hạn."
      );
    }
    assertCurrentKey(this.cipher, preauth.tokenKeyId);
    const tokens = await this.provider.exchangeAuthorizationCode({
      callbackUrl,
      expectedNonce: this.cipher.open(preauth.nonceCiphertext, "preauth:nonce"),
      expectedState: state,
      pkceVerifier: this.cipher.open(
        preauth.pkceVerifierCiphertext,
        "preauth:pkce"
      )
    });
    const sessionToken = randomOpaqueToken();
    await this.store.createSession(
      createSessionInput(this.env, this.cipher, tokens, sessionToken, now)
    );
    return { returnPath: preauth.returnPath, sessionToken };
  }

  async requireSession(
    sessionToken: string | undefined,
    now = new Date()
  ): Promise<ValidSession> {
    if (!sessionToken) throw invalidSessionError();
    const tokenHash = hashOpaqueToken(sessionToken);
    let session = await requireUsableSession(
      this.env,
      this.store,
      tokenHash,
      await this.store.findSession(tokenHash),
      now
    );
    if (isFresh(session, now)) return toValidSession(this.cipher, session);
    if (!session.refreshTokenCiphertext) {
      await this.store.revokeSession(tokenHash, now);
      throw invalidSessionError();
    }

    for (let attempt = 0; attempt < REFRESH_WAIT_ATTEMPTS; attempt += 1) {
      const observedAt = attempt === 0 ? now : new Date();
      const lease = await this.store.acquireRefreshLease(tokenHash, observedAt);
      if (lease) {
        return refreshLeasedSession(
          lease,
          observedAt,
          this.store,
          this.cipher,
          this.provider
        );
      }
      await delay(REFRESH_POLL_INTERVAL_MS);
      session = await requireUsableSession(
        this.env,
        this.store,
        tokenHash,
        await this.store.findSession(tokenHash),
        new Date()
      );
      if (isFresh(session, new Date())) {
        return toValidSession(this.cipher, session);
      }
    }
    throw new AuthError(
      "issuer_unavailable",
      503,
      "Làm mới phiên đăng nhập tạm thời chưa sẵn sàng."
    );
  }

  async logout(sessionToken: string | undefined, now = new Date()): Promise<URL | null> {
    if (!sessionToken) return null;
    const session = await this.store.revokeSession(
      hashOpaqueToken(sessionToken),
      now
    );
    if (!session || session.tokenKeyId !== this.cipher.keyId) return null;
    if (session.refreshTokenCiphertext) {
      try {
        await this.provider.bestEffortRevoke(
          this.cipher.open(session.refreshTokenCiphertext, "session:refresh")
        );
      } catch {
        // Local revocation remains authoritative when provider cleanup fails.
      }
    }
    try {
      const idToken = session.idTokenCiphertext
        ? this.cipher.open(session.idTokenCiphertext, "session:id")
        : undefined;
      return await this.provider.buildEndSessionRedirect(idToken, this.env.baseUrl);
    } catch {
      return null;
    }
  }

  private encrypt(value: string, purpose: string) {
    return encryptedValue(this.cipher, value, purpose);
  }
}

function isFresh(session: StoredSession, now: Date): boolean {
  return session.accessTokenExpiresAt.getTime() > now.getTime() + REFRESH_SKEW_MS;
}
