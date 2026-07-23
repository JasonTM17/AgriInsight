export const SESSION_COOKIE_NAME = "__Host-agriinsight-auth-spike";
export const PREAUTH_COOKIE_NAME = "__Host-agriinsight-auth-preauth";

export const SECURE_COOKIE_OPTIONS = Object.freeze({
  httpOnly: true,
  secure: true,
  sameSite: "lax" as const,
  path: "/",
});

export function sessionCookieOptions(maxAge: number) {
  return { ...SECURE_COOKIE_OPTIONS, maxAge };
}

export function expiredCookieOptions() {
  return { ...SECURE_COOKIE_OPTIONS, maxAge: 0, expires: new Date(0) };
}
