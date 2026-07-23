import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { authErrorResponse } from "@/server/auth/auth-http";
import {
  CSRF_COOKIE_NAME,
  PREAUTH_COOKIE_NAME,
  SECURE_SCRIPT_COOKIE,
  SESSION_COOKIE_NAME,
  expiredHttpOnlyCookieOptions,
  sessionCookieOptions
} from "@/server/auth/cookie-policy";
import { createCsrfToken } from "@/server/auth/csrf";
import { getAuthRuntime } from "@/server/auth/runtime";
import { assertTrustedRequest } from "@/server/config/environment";

export async function GET(request: NextRequest) {
  try {
    const runtime = getAuthRuntime();
    const trustedUrl = assertTrustedRequest(request, runtime.env);
    if (trustedUrl.pathname !== runtime.env.callbackUrl.pathname) {
      throw new Error("Unexpected callback path");
    }
    const callbackUrl = new URL(runtime.env.callbackUrl);
    callbackUrl.search = trustedUrl.search;
    const result = await runtime.auth.completeCallback(
      callbackUrl,
      request.cookies.get(PREAUTH_COOKIE_NAME)?.value
    );
    const response = NextResponse.redirect(
      new URL(result.returnPath, runtime.env.baseUrl),
      303
    );
    response.cookies.set(
      SESSION_COOKIE_NAME,
      result.sessionToken,
      sessionCookieOptions(runtime.env.sessionLifetimeSeconds)
    );
    response.cookies.set(
      CSRF_COOKIE_NAME,
      createCsrfToken(result.sessionToken, runtime.env.csrfKey),
      {
        ...SECURE_SCRIPT_COOKIE,
        maxAge: runtime.env.sessionLifetimeSeconds
      }
    );
    response.cookies.set(
      PREAUTH_COOKIE_NAME,
      "",
      expiredHttpOnlyCookieOptions()
    );
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    return authErrorResponse(error);
  }
}
