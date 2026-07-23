import { AuthError } from "@/server/auth/auth-error";
import { randomOpaqueToken, signBoundValue, verifyBoundValue } from "@/server/auth/token-crypto";

export const CSRF_HEADER_NAME = "x-agriinsight-csrf";

export function createCsrfToken(sessionToken: string, key: Buffer): string {
  return signBoundValue(randomOpaqueToken(), sessionToken, key);
}

export function assertCsrf(
  request: Request,
  cookieValue: string | undefined,
  sessionToken: string | undefined,
  key: Buffer
): void {
  const headerValue = request.headers.get(CSRF_HEADER_NAME);
  if (
    !cookieValue ||
    !sessionToken ||
    !headerValue ||
    headerValue !== cookieValue ||
    !verifyBoundValue(cookieValue, sessionToken, key)
  ) {
    throw new AuthError("invalid_request", 403, "CSRF token không hợp lệ.");
  }
}
