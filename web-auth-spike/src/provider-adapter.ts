import {
  AuthError,
} from "./auth-errors.ts";
import type { AuthEnvironment } from "./env.ts";
import type { ProviderTokens, RefreshedProviderTokens } from "./session-contracts.ts";

export class ProviderRefreshError extends Error {
  constructor(
    readonly invalidGrant: boolean,
    cause: unknown,
  ) {
    super(invalidGrant ? "Provider rejected the refresh grant" : "Provider refresh failed");
    this.name = "ProviderRefreshError";
    this.cause = cause;
  }
}

export type AuthorizationInput = Readonly<{
  codeChallenge: string;
  nonce: string;
  state: string;
}>;

export type CallbackInput = Readonly<{
  callbackUrl: URL;
  expectedNonce: string;
  expectedState: string;
  pkceVerifier: string;
}>;

export interface OidcProviderAdapter {
  createPkceVerifier(): string;
  calculatePkceChallenge(verifier: string): Promise<string>;
  buildAuthorizationRedirect(input: AuthorizationInput): Promise<URL>;
  exchangeAuthorizationCode(input: CallbackInput): Promise<ProviderTokens>;
  refresh(refreshToken: string): Promise<RefreshedProviderTokens>;
  bestEffortRevoke(refreshToken: string): Promise<void>;
  buildEndSessionRedirect(
    idToken: string | undefined,
    returnUrl: URL,
  ): Promise<URL | null>;
  capabilities(): Promise<Readonly<{ endSession: boolean; revocation: boolean }>>;
}

export function authorizationValidationFailure(cause: unknown): AuthError {
  return new AuthError(
    "invalid_nonce",
    400,
    "Authentication response validation failed.",
    cause,
  );
}

export function tokenExpiry(expiresIn: number | undefined): Date {
  if (!expiresIn || expiresIn <= 0) {
    throw new AuthError(
      "invalid_nonce",
      400,
      "Authentication response validation failed.",
    );
  }
  return new Date(Date.now() + expiresIn * 1000);
}

export function exactIssuer(actual: string, env: AuthEnvironment): void {
  if (actual !== env.issuer.href.replace(/\/$/, "")) {
    throw new Error("OIDC discovery issuer does not exactly match configuration");
  }
}
