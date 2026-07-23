import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { authErrorResponse } from "../../../../auth-http";
import {
  PREAUTH_COOKIE_NAME,
  SESSION_COOKIE_NAME,
  expiredCookieOptions,
} from "../../../../cookie-policy";
import { assertSameOriginMutation, assertTrustedRequest } from "../../../../env";
import { getAuthRuntime } from "../../../../runtime";

export async function POST(request: NextRequest) {
  try {
    const runtime = getAuthRuntime();
    assertTrustedRequest(request, runtime.env);
    assertSameOriginMutation(request, runtime.env);
    const providerLogout = await runtime.auth.logout(
      request.cookies.get(SESSION_COOKIE_NAME)?.value,
    );
    const response = NextResponse.redirect(providerLogout ?? runtime.env.baseUrl, 303);
    response.cookies.set(SESSION_COOKIE_NAME, "", expiredCookieOptions());
    response.cookies.set(PREAUTH_COOKIE_NAME, "", expiredCookieOptions());
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    return authErrorResponse(error);
  }
}
