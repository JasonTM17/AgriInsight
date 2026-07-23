import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { authErrorResponse } from "../../../../auth-http";
import {
  PREAUTH_COOKIE_NAME,
  SECURE_COOKIE_OPTIONS,
} from "../../../../cookie-policy";
import { assertTrustedRequest } from "../../../../env";
import { getAuthRuntime } from "../../../../runtime";

export async function GET(request: NextRequest) {
  try {
    const runtime = getAuthRuntime();
    const url = assertTrustedRequest(request, runtime.env);
    const login = await runtime.auth.beginLogin(url.searchParams.get("returnTo"));
    const response = NextResponse.redirect(login.redirectUrl, 302);
    response.cookies.set(PREAUTH_COOKIE_NAME, login.browserBinding, {
      ...SECURE_COOKIE_OPTIONS,
      maxAge: 300,
    });
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    return authErrorResponse(error);
  }
}
