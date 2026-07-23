import "server-only";

import {
  ClientSecretBasic,
  ResponseBodyError,
  allowInsecureRequests,
  authorizationCodeGrant,
  buildAuthorizationUrl,
  buildEndSessionUrl,
  calculatePKCECodeChallenge,
  discovery,
  randomPKCECodeVerifier,
  refreshTokenGrant,
  tokenRevocation,
  type Configuration
} from "openid-client";

import {
  ProviderRefreshError,
  assertExactIssuer,
  authorizationValidationFailure,
  tokenExpiry,
  type AuthorizationInput,
  type CallbackInput,
  type OidcProviderAdapter
} from "@/server/auth/provider";
import type {
  ProviderTokens,
  RefreshedProviderTokens
} from "@/server/auth/session-contracts";
import type { WebEnvironment } from "@/server/config/environment";

export class OpenIdClientProvider implements OidcProviderAdapter {
  private configuration?: Promise<Configuration>;

  constructor(private readonly env: WebEnvironment) {}

  createPkceVerifier(): string {
    return randomPKCECodeVerifier();
  }

  calculatePkceChallenge(verifier: string): Promise<string> {
    return calculatePKCECodeChallenge(verifier);
  }

  async buildAuthorizationRedirect(input: AuthorizationInput): Promise<URL> {
    return buildAuthorizationUrl(await this.config(), {
      client_id: this.env.clientId,
      code_challenge: input.codeChallenge,
      code_challenge_method: "S256",
      nonce: input.nonce,
      redirect_uri: this.env.callbackUrl.href,
      response_type: "code",
      scope: "openid profile email",
      state: input.state
    });
  }

  async exchangeAuthorizationCode(input: CallbackInput): Promise<ProviderTokens> {
    try {
      const tokens = await authorizationCodeGrant(
        await this.config(),
        input.callbackUrl,
        {
          expectedNonce: input.expectedNonce,
          expectedState: input.expectedState,
          idTokenExpected: true,
          pkceCodeVerifier: input.pkceVerifier
        }
      );
      const subject = tokens.claims()?.sub;
      if (!subject || !tokens.access_token) {
        throw new Error("Validated token response omitted subject or access token");
      }
      return {
        accessToken: tokens.access_token,
        accessTokenExpiresAt: tokenExpiry(tokens.expiresIn()),
        idToken: tokens.id_token,
        refreshToken: tokens.refresh_token,
        subject
      };
    } catch (error) {
      throw authorizationValidationFailure(error);
    }
  }

  async refresh(refreshToken: string): Promise<RefreshedProviderTokens> {
    try {
      const tokens = await refreshTokenGrant(await this.config(), refreshToken);
      if (!tokens.access_token) throw new Error("Refresh omitted access token");
      return {
        accessToken: tokens.access_token,
        accessTokenExpiresAt: tokenExpiry(tokens.expiresIn()),
        idToken: tokens.id_token,
        refreshToken: tokens.refresh_token
      };
    } catch (error) {
      throw new ProviderRefreshError(
        error instanceof ResponseBodyError && error.error === "invalid_grant",
        error
      );
    }
  }

  async bestEffortRevoke(refreshToken: string): Promise<void> {
    const config = await this.config();
    if (!config.serverMetadata().revocation_endpoint) return;
    await tokenRevocation(config, refreshToken, {
      token_type_hint: "refresh_token"
    });
  }

  async buildEndSessionRedirect(
    idToken: string | undefined,
    returnUrl: URL
  ): Promise<URL | null> {
    const config = await this.config();
    if (!config.serverMetadata().end_session_endpoint) return null;
    return buildEndSessionUrl(config, {
      client_id: this.env.clientId,
      ...(idToken ? { id_token_hint: idToken } : {}),
      post_logout_redirect_uri: returnUrl.href
    });
  }

  private config(): Promise<Configuration> {
    const loopback = ["localhost", "127.0.0.1", "::1"].includes(
      this.env.issuer.hostname
    );
    this.configuration ??= discovery(
      this.env.issuer,
      this.env.clientId,
      {
        client_secret: this.env.clientSecret,
        token_endpoint_auth_method: "client_secret_basic"
      },
      ClientSecretBasic(this.env.clientSecret),
      { execute: loopback ? [allowInsecureRequests] : [] }
    ).then((configuration) => {
      assertExactIssuer(configuration.serverMetadata().issuer, this.env);
      if (!configuration.serverMetadata().supportsPKCE()) {
        throw new Error("OIDC issuer does not advertise PKCE support");
      }
      configuration.timeout = 5;
      return configuration;
    });
    return this.configuration;
  }
}
