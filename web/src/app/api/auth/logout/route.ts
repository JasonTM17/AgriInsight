import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { authErrorResponse } from "@/server/auth/auth-http";
import {
  CSRF_COOKIE_NAME,
  PREAUTH_COOKIE_NAME,
  SESSION_COOKIE_NAME,
  expiredHttpOnlyCookieOptions,
  expiredScriptCookieOptions
} from "@/server/auth/cookie-policy";
import { assertCsrf } from "@/server/auth/csrf";
import { assertSameOriginMutation } from "@/server/auth/origin-guard";
import { getAuthRuntime } from "@/server/auth/runtime";
import { assertTrustedRequest } from "@/server/config/environment";

export async function POST(request: NextRequest) {
  try {
    const runtime = getAuthRuntime();
    assertTrustedRequest(request, runtime.env);
    assertSameOriginMutation(request, runtime.env);
    const sessionToken = request.cookies.get(SESSION_COOKIE_NAME)?.value;
    assertCsrf(
      request,
      request.cookies.get(CSRF_COOKIE_NAME)?.value,
      sessionToken,
      runtime.env.csrfKey
    );
    const providerLogout = await runtime.auth.logout(sessionToken);
    const response = NextResponse.redirect(
      providerLogout ?? runtime.env.baseUrl,
      303
    );
    response.cookies.set(
      SESSION_COOKIE_NAME,
      "",
      expiredHttpOnlyCookieOptions()
    );
    response.cookies.set(
      PREAUTH_COOKIE_NAME,
      "",
      expiredHttpOnlyCookieOptions()
    );
    response.cookies.set(
      CSRF_COOKIE_NAME,
      "",
      expiredScriptCookieOptions()
    );
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    return authErrorResponse(error);
  }
}
