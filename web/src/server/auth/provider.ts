import "server-only";

import { AuthError } from "@/server/auth/auth-error";
import type {
  ProviderTokens,
  RefreshedProviderTokens
} from "@/server/auth/session-contracts";
import type { WebEnvironment } from "@/server/config/environment";

export class ProviderRefreshError extends Error {
  constructor(
    readonly invalidGrant: boolean,
    cause: unknown
  ) {
    super(
      invalidGrant
        ? "Provider rejected the refresh grant"
        : "Provider refresh failed",
      { cause }
    );
    this.name = "ProviderRefreshError";
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
    returnUrl: URL
  ): Promise<URL | null>;
}

export function authorizationValidationFailure(cause: unknown): AuthError {
  return new AuthError(
    "invalid_nonce",
    400,
    "Phản hồi đăng nhập không vượt qua kiểm tra state/nonce.",
    { cause }
  );
}

export function tokenExpiry(expiresIn: number | undefined): Date {
  if (!expiresIn || expiresIn <= 0) {
    throw new AuthError(
      "invalid_nonce",
      400,
      "Phản hồi đăng nhập thiếu thời hạn access token."
    );
  }
  return new Date(Date.now() + expiresIn * 1000);
}

export function assertExactIssuer(actual: string, env: WebEnvironment): void {
  if (actual !== env.issuer.href.replace(/\/$/, "")) {
    throw new Error("OIDC discovery issuer does not exactly match configuration");
  }
}
