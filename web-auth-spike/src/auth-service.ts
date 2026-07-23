import { setTimeout as delay } from "node:timers/promises";

import { AuthError, invalidSessionError } from "./auth-errors.ts";
import type { AuthEnvironment } from "./env.ts";
import type { OidcProviderAdapter } from "./provider-adapter.ts";
import { allowlistedReturnPath } from "./request-policy.ts";
import type {
  EncryptedValue,
  ProviderTokens,
  SessionStore,
  StoredSession,
} from "./session-contracts.ts";
import {
  refreshLeasedSession,
  type ValidSession,
} from "./session-refresh-coordinator.ts";
import { TokenCipher, hashOpaqueToken, randomOpaqueToken } from "./token-crypto.ts";

const PREAUTH_LIFETIME_MS = 5 * 60 * 1000;
const REFRESH_SKEW_MS = 30 * 1000;
const REFRESH_POLL_INTERVAL_MS = 50;
const REFRESH_WAIT_ATTEMPTS = 120;

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
    private readonly env: AuthEnvironment,
    private readonly store: SessionStore,
    private readonly cipher: TokenCipher,
    private readonly provider: OidcProviderAdapter,
  ) {}

  async beginLogin(candidateReturnPath?: string | null, now = new Date()): Promise<LoginStart> {
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
      stateHash: hashOpaqueToken(state),
    });
    const redirectUrl = await this.provider.buildAuthorizationRedirect({
      codeChallenge,
      nonce,
      state,
    });
    return { browserBinding, redirectUrl };
  }

  async completeCallback(
    callbackUrl: URL,
    browserBinding: string | undefined,
    now = new Date(),
  ): Promise<CallbackResult> {
    const state = callbackUrl.searchParams.get("state");
    const code = callbackUrl.searchParams.get("code");
    if (!state || !code || !browserBinding) {
      throw new AuthError("invalid_request", 400, "Authentication response is incomplete.");
    }
    if (callbackUrl.searchParams.has("error")) {
      throw new AuthError("invalid_request", 400, "Authentication was not completed.");
    }

    const preauth = await this.store.consumePreauth(
      hashOpaqueToken(state),
      hashOpaqueToken(browserBinding),
      now,
    );
    if (!preauth) {
      throw new AuthError("invalid_state", 400, "Authentication state is invalid or expired.");
    }
    this.assertCurrentKey(preauth.tokenKeyId);

    const tokens = await this.provider.exchangeAuthorizationCode({
      callbackUrl,
      expectedNonce: this.cipher.open(preauth.nonceCiphertext, "preauth:nonce"),
      expectedState: state,
      pkceVerifier: this.cipher.open(preauth.pkceVerifierCiphertext, "preauth:pkce"),
    });
    const sessionToken = randomOpaqueToken();
    await this.store.createSession(
      this.createSessionInput(tokens, sessionToken, now),
    );
    return { returnPath: preauth.returnPath, sessionToken };
  }

  async requireSession(sessionToken: string | undefined, now = new Date()): Promise<ValidSession> {
    if (!sessionToken) throw invalidSessionError();
    const tokenHash = hashOpaqueToken(sessionToken);
    let session = await this.store.findSession(tokenHash);
    session = await this.requireUsable(tokenHash, session, now);
    if (isFresh(session, now)) return this.validSession(session);
    if (!session.refreshTokenCiphertext) {
      await this.store.revokeSession(tokenHash, now);
      throw invalidSessionError();
    }

    for (let attempt = 0; attempt < REFRESH_WAIT_ATTEMPTS; attempt += 1) {
      const attemptNow = attempt === 0 ? now : new Date();
      const lease = await this.store.acquireRefreshLease(tokenHash, attemptNow);
      if (lease) {
        return refreshLeasedSession(
          lease,
          attemptNow,
          this.store,
          this.cipher,
          this.provider,
        );
      }
      await delay(REFRESH_POLL_INTERVAL_MS);
      session = await this.store.findSession(tokenHash);
      const observedAt = new Date();
      session = await this.requireUsable(tokenHash, session, observedAt);
      if (isFresh(session, observedAt)) return this.validSession(session);
    }
    throw new AuthError(
      "issuer_unavailable",
      503,
      "Session refresh is temporarily unavailable.",
    );
  }

  async logout(
    sessionToken: string | undefined,
    now = new Date(),
  ): Promise<URL | null> {
    if (!sessionToken) return null;
    const tokenHash = hashOpaqueToken(sessionToken);
    const session = await this.store.revokeSession(tokenHash, now);
    if (!session || session.tokenKeyId !== this.cipher.keyId) return null;

    if (session.refreshTokenCiphertext) try {
      const refreshToken = this.cipher.open(session.refreshTokenCiphertext, "session:refresh");
      await this.provider.bestEffortRevoke(refreshToken);
    } catch {
      // Local revocation is authoritative; issuer logout/revocation is optional.
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

  private createSessionInput(tokens: ProviderTokens, sessionToken: string, now: Date) {
    return {
      accessToken: this.encrypt(tokens.accessToken, "session:access"),
      accessTokenExpiresAt: tokens.accessTokenExpiresAt,
      idToken: tokens.idToken ? this.encrypt(tokens.idToken, "session:id") : undefined,
      issuer: this.env.issuer.href.replace(/\/$/, ""),
      refreshToken: tokens.refreshToken
        ? this.encrypt(tokens.refreshToken, "session:refresh")
        : undefined,
      sessionExpiresAt: new Date(now.getTime() + this.env.sessionLifetimeSeconds * 1000),
      sessionTokenHash: hashOpaqueToken(sessionToken),
      subject: tokens.subject,
    };
  }

  private validSession(session: StoredSession): ValidSession {
    this.assertCurrentKey(session.tokenKeyId);
    return {
      accessToken: this.cipher.open(session.accessTokenCiphertext, "session:access"),
      expiresAt: session.accessTokenExpiresAt,
      sessionVersion: session.sessionVersion,
      subject: session.subject,
    };
  }

  private encrypt(value: string, purpose: string): EncryptedValue {
    return { ciphertext: this.cipher.seal(value, purpose), keyId: this.cipher.keyId };
  }

  private assertCurrentKey(keyId: string): void {
    if (keyId !== this.cipher.keyId) throw invalidSessionError();
  }

  private async requireUsable(
    tokenHash: Buffer,
    session: StoredSession | null,
    now: Date,
  ): Promise<StoredSession> {
    if (!session || session.revokedAt || session.sessionExpiresAt <= now) {
      throw invalidSessionError();
    }
    if (session.issuer !== this.env.issuer.href.replace(/\/$/, "")) {
      await this.store.revokeSession(tokenHash, now);
      throw invalidSessionError();
    }
    return session;
  }
}

function isFresh(session: StoredSession, now: Date): boolean {
  return session.accessTokenExpiresAt.getTime() > now.getTime() + REFRESH_SKEW_MS;
}
