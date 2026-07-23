import { randomUUID } from "node:crypto";

import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { authErrorResponse } from "@/server/auth/auth-http";
import { SESSION_COOKIE_NAME } from "@/server/auth/cookie-policy";
import { getAuthorizationContext } from "@/server/auth/authorization-context";
import { getAuthRuntime } from "@/server/auth/runtime";
import { assertTrustedRequest } from "@/server/config/environment";

export async function GET(request: NextRequest) {
  try {
    const runtime = getAuthRuntime();
    assertTrustedRequest(request, runtime.env);
    const session = await runtime.auth.requireSession(
      request.cookies.get(SESSION_COOKIE_NAME)?.value
    );
    const identity = await getAuthorizationContext(
      runtime.env,
      session.accessToken,
      request.headers.get("x-correlation-id") ?? randomUUID()
    );
    return NextResponse.json(
      {
        displayName: identity.displayName,
        email: identity.email,
        permissions: [...identity.permissions],
        profileId: identity.profileId,
        roles: [...identity.roles],
        tenantCode: identity.tenantCode,
        tenantId: identity.tenantId
      },
      {
        headers: {
          "Cache-Control": "no-store"
        }
      }
    );
  } catch (error) {
    return authErrorResponse(error);
  }
}
