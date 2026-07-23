export type AuthErrorCode =
  | "invalid_request"
  | "invalid_host"
  | "invalid_state"
  | "expired_state"
  | "invalid_nonce"
  | "invalid_session"
  | "issuer_unavailable";

export class AuthError extends Error {
  constructor(
    readonly code: AuthErrorCode,
    readonly status: number,
    message: string,
    readonly cause?: unknown,
  ) {
    super(message);
    this.name = "AuthError";
  }
}

export function sanitizedAuthError(error: unknown): AuthError {
  if (error instanceof AuthError) {
    return error;
  }
  return new AuthError(
    "issuer_unavailable",
    503,
    "The identity service is temporarily unavailable.",
    error,
  );
}

export function invalidSessionError(): AuthError {
  return new AuthError("invalid_session", 401, "A valid local session is required.");
}
