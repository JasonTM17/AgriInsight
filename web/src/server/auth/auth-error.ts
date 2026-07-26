export type AuthErrorCode =
  | "authorization_denied"
  | "invalid_host"
  | "invalid_nonce"
  | "invalid_origin"
  | "invalid_request"
  | "invalid_session"
  | "invalid_state"
  | "issuer_unavailable"
  | "rate_limited";

export class AuthError extends Error {
  constructor(
    readonly code: AuthErrorCode,
    readonly status: number,
    message: string,
    options?: ErrorOptions
  ) {
    super(message, options);
    this.name = "AuthError";
  }
}

export function invalidSessionError(): AuthError {
  return new AuthError(
    "invalid_session",
    401,
    "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
  );
}

export function sanitizedAuthError(error: unknown): AuthError {
  if (error instanceof AuthError) return error;
  return new AuthError(
    "issuer_unavailable",
    503,
    "Dịch vụ đăng nhập tạm thời chưa sẵn sàng."
  );
}
