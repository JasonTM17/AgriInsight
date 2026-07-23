export const SESSION_COOKIE_NAME = "__Host-agriinsight-session";
export const PREAUTH_COOKIE_NAME = "__Host-agriinsight-preauth";
export const CSRF_COOKIE_NAME = "__Host-agriinsight-csrf";

export const SECURE_HTTP_ONLY_COOKIE = Object.freeze({
  httpOnly: true,
  secure: true,
  sameSite: "lax" as const,
  path: "/"
});

export const SECURE_SCRIPT_COOKIE = Object.freeze({
  httpOnly: false,
  secure: true,
  sameSite: "lax" as const,
  path: "/"
});

export function sessionCookieOptions(maxAge: number) {
  return { ...SECURE_HTTP_ONLY_COOKIE, maxAge };
}

export function expiredHttpOnlyCookieOptions() {
  return {
    ...SECURE_HTTP_ONLY_COOKIE,
    maxAge: 0,
    expires: new Date(0)
  };
}

export function expiredScriptCookieOptions() {
  return {
    ...SECURE_SCRIPT_COOKIE,
    maxAge: 0,
    expires: new Date(0)
  };
}
