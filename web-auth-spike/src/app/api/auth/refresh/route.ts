import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { authErrorResponse, NO_STORE_HEADERS } from "../../../../auth-http";
import { SESSION_COOKIE_NAME } from "../../../../cookie-policy";
import { assertSameOriginMutation, assertTrustedRequest } from "../../../../env";
import { getAuthRuntime } from "../../../../runtime";

export async function POST(request: NextRequest) {
  try {
    const runtime = getAuthRuntime();
    assertTrustedRequest(request, runtime.env);
    assertSameOriginMutation(request, runtime.env);
    const session = await runtime.auth.requireSession(
      request.cookies.get(SESSION_COOKIE_NAME)?.value,
    );
    return NextResponse.json(
      { refreshed: true, sessionVersion: session.sessionVersion },
      { headers: NO_STORE_HEADERS },
    );
  } catch (error) {
    return authErrorResponse(error);
  }
}
