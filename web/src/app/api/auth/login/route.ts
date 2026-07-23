import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { authErrorResponse } from "@/server/auth/auth-http";
import {
  PREAUTH_COOKIE_NAME,
  SECURE_HTTP_ONLY_COOKIE
} from "@/server/auth/cookie-policy";
import { getAuthRuntime } from "@/server/auth/runtime";
import { assertTrustedRequest } from "@/server/config/environment";

export async function GET(request: NextRequest) {
  try {
    const runtime = getAuthRuntime();
    const requestUrl = assertTrustedRequest(request, runtime.env);
    const login = await runtime.auth.beginLogin(
      requestUrl.searchParams.get("returnTo")
    );
    const response = NextResponse.redirect(login.redirectUrl, 302);
    response.cookies.set(PREAUTH_COOKIE_NAME, login.browserBinding, {
      ...SECURE_HTTP_ONLY_COOKIE,
      maxAge: 300
    });
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    return authErrorResponse(error);
  }
}
