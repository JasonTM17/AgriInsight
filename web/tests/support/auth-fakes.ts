import type { OidcProviderAdapter } from "@/server/auth/provider";
import type {
  ConsumedPreauth,
  CreatePreauthInput,
  CreateSessionInput,
  ProviderTokens,
  RefreshLease,
  RefreshedProviderTokens,
  SessionStore,
  StoredSession
} from "@/server/auth/session-contracts";

export class MemorySessionStore implements SessionStore {
  preauth: CreatePreauthInput | null = null;
  session: StoredSession | null = null;

  async createPreauth(input: CreatePreauthInput): Promise<void> {
    this.preauth = input;
  }

  async consumePreauth(
    stateHash: Buffer,
    bindingHash: Buffer,
    now: Date
  ): Promise<ConsumedPreauth | null> {
    const preauth = this.preauth;
    if (
      !preauth ||
      preauth.expiresAt <= now ||
      !stateHash.equals(preauth.stateHash) ||
      !bindingHash.equals(preauth.browserBindingHash)
    ) {
      return null;
    }
    this.preauth = null;
    return {
      nonceCiphertext: preauth.nonce.ciphertext,
      pkceVerifierCiphertext: preauth.pkceVerifier.ciphertext,
      returnPath: preauth.returnPath,
      tokenKeyId: preauth.nonce.keyId
    };
  }

  async createSession(input: CreateSessionInput): Promise<void> {
    this.session = {
      accessTokenCiphertext: input.accessToken.ciphertext,
      accessTokenExpiresAt: input.accessTokenExpiresAt,
      id: "session-id",
      idTokenCiphertext: input.idToken?.ciphertext ?? null,
      issuer: input.issuer,
      refreshLeaseExpiresAt: null,
      refreshLeaseId: null,
      refreshTokenCiphertext: input.refreshToken?.ciphertext ?? null,
      revokedAt: null,
      sessionExpiresAt: input.sessionExpiresAt,
      sessionTokenHash: input.sessionTokenHash,
      sessionVersion: 1,
      subject: input.subject,
      tokenKeyId: input.accessToken.keyId
    };
  }

  async findSession(): Promise<StoredSession | null> {
    return this.session;
  }

  async acquireRefreshLease(): Promise<RefreshLease | null> {
    return null;
  }

  async rotateSession(): Promise<boolean> {
    return false;
  }

  async finishTransientRefreshFailure(): Promise<void> {}

  async revokeRefreshLease(): Promise<void> {}

  async revokeSession(
    _sessionTokenHash: Buffer,
    now: Date
  ): Promise<StoredSession | null> {
    const previous = this.session;
    if (previous) this.session = { ...previous, revokedAt: now };
    return previous;
  }
}

export class FakeProvider implements OidcProviderAdapter {
  callbackInput?: Parameters<OidcProviderAdapter["exchangeAuthorizationCode"]>[0];

  createPkceVerifier(): string {
    return "pkce-verifier";
  }

  async calculatePkceChallenge(): Promise<string> {
    return "pkce-challenge";
  }

  async buildAuthorizationRedirect(
    input: Parameters<OidcProviderAdapter["buildAuthorizationRedirect"]>[0]
  ): Promise<URL> {
    const url = new URL("https://issuer.example/authorize");
    url.searchParams.set("state", input.state);
    url.searchParams.set("nonce", input.nonce);
    return url;
  }

  async exchangeAuthorizationCode(
    input: Parameters<OidcProviderAdapter["exchangeAuthorizationCode"]>[0]
  ): Promise<ProviderTokens> {
    this.callbackInput = input;
    return {
      accessToken: "provider-access-secret",
      accessTokenExpiresAt: new Date("2026-07-24T00:00:00Z"),
      refreshToken: "provider-refresh-secret",
      subject: "subject-1"
    };
  }

  async refresh(): Promise<RefreshedProviderTokens> {
    throw new Error("not implemented by fake");
  }

  async bestEffortRevoke(): Promise<void> {}

  async buildEndSessionRedirect(): Promise<URL | null> {
    return null;
  }
}
