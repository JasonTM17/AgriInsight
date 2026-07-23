import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { authErrorResponse } from "../../../../../auth-http";
import {
  PREAUTH_COOKIE_NAME,
  SESSION_COOKIE_NAME,
  expiredCookieOptions,
  sessionCookieOptions,
} from "../../../../../cookie-policy";
import { assertTrustedRequest } from "../../../../../env";
import { getAuthRuntime } from "../../../../../runtime";

export async function GET(request: NextRequest) {
  try {
    const runtime = getAuthRuntime();
    const callbackUrl = assertTrustedRequest(request, runtime.env);
    if (callbackUrl.href.split("?")[0] !== runtime.env.callbackUrl.href) {
      throw new Error("Unexpected callback URL");
    }
    const result = await runtime.auth.completeCallback(
      callbackUrl,
      request.cookies.get(PREAUTH_COOKIE_NAME)?.value,
    );
    const response = NextResponse.redirect(
      new URL(result.returnPath, runtime.env.baseUrl),
      303,
    );
    response.cookies.set(
      SESSION_COOKIE_NAME,
      result.sessionToken,
      sessionCookieOptions(runtime.env.sessionLifetimeSeconds),
    );
    response.cookies.set(PREAUTH_COOKIE_NAME, "", expiredCookieOptions());
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    return authErrorResponse(error);
  }
}
