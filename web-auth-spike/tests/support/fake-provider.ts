import { AuthError } from "../../src/auth-errors.ts";
import {
  ProviderRefreshError,
  type OidcProviderAdapter,
} from "../../src/provider-adapter.ts";
import type {
  ProviderTokens,
  RefreshedProviderTokens,
} from "../../src/session-contracts.ts";

export class FakeProvider implements OidcProviderAdapter {
  exchangeFailure?: "nonce";
  refreshBehavior: "success" | "invalid-grant" | "transient" = "success";
  refreshCalls = 0;
  refreshDelayMs = 60;
  revokedTokens: string[] = [];
  revokeFails = false;
  lastState?: string;
  tokens: ProviderTokens = {
    accessToken: "fixture-access-marker",
    accessTokenExpiresAt: new Date(Date.now() + 120_000),
    idToken: "fixture-id-marker",
    refreshToken: "fixture-refresh-marker",
    subject: "00000000-0000-4000-8000-000000000001",
  };

  createPkceVerifier(): string {
    return "v".repeat(64);
  }

  async calculatePkceChallenge(): Promise<string> {
    return "challenge";
  }

  async buildAuthorizationRedirect(input: { state: string }): Promise<URL> {
    this.lastState = input.state;
    return new URL(`http://localhost:58080/authorize?state=${input.state}`);
  }

  async exchangeAuthorizationCode(): Promise<ProviderTokens> {
    if (this.exchangeFailure === "nonce") {
      throw new AuthError("invalid_nonce", 400, "Authentication response validation failed.");
    }
    return this.tokens;
  }

  async refresh(): Promise<RefreshedProviderTokens> {
    this.refreshCalls += 1;
    await new Promise((resolve) => setTimeout(resolve, this.refreshDelayMs));
    if (this.refreshBehavior !== "success") {
      throw new ProviderRefreshError(
        this.refreshBehavior === "invalid-grant",
        new Error("fixture"),
      );
    }
    return {
      accessToken: "rotated-access-marker",
      accessTokenExpiresAt: new Date(Date.now() + 120_000),
      refreshToken: "rotated-refresh-marker",
    };
  }

  async bestEffortRevoke(refreshToken: string): Promise<void> {
    this.revokedTokens.push(refreshToken);
    if (this.revokeFails) throw new Error("issuer unavailable");
  }

  async buildEndSessionRedirect(
    _idToken: string | undefined,
    returnUrl: URL,
  ): Promise<URL> {
    const target = new URL("http://localhost:58080/logout");
    target.searchParams.set("post_logout_redirect_uri", returnUrl.href);
    return target;
  }

  async capabilities() {
    return { endSession: true, revocation: true };
  }
}
