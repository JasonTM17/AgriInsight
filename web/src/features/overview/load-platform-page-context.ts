import "server-only";

import { randomUUID } from "node:crypto";
import { cache } from "react";

import { cookies } from "next/headers";

import { getAuthorizationContext } from "@/server/auth/authorization-context";
import { SESSION_COOKIE_NAME } from "@/server/auth/cookie-policy";
import { getAuthRuntime } from "@/server/auth/runtime";

export const loadPlatformPageContext = cache(async () => {
  try {
    const runtime = getAuthRuntime();
    const sessionToken = (await cookies()).get(SESSION_COOKIE_NAME)?.value;
    const session = await runtime.auth.requireSession(sessionToken);
    const correlationId = randomUUID();
    const identity = await getAuthorizationContext(
      runtime.env,
      session.accessToken,
      correlationId
    );
    return {
      accessToken: session.accessToken,
      correlationId,
      env: runtime.env,
      identity,
      sessionVersion: session.sessionVersion
    } as const;
  } catch {
    return null;
  }
});
