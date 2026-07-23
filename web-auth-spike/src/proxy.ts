import type { NextRequest } from "next/server.js";
import { NextResponse } from "next/server.js";

import { SESSION_COOKIE_NAME } from "./cookie-policy.ts";

export function proxy(request: NextRequest) {
  const allowedHost = process.env.AUTH_SPIKE_ALLOWED_HOST ?? "localhost:3100";
  if (request.headers.get("host") !== allowedHost) {
    return new NextResponse("Invalid request host", { status: 400 });
  }
  if (!request.cookies.has(SESSION_COOKIE_NAME)) {
    const target = new URL("/api/auth/login", request.url);
    target.searchParams.set("returnTo", "/protected");
    return NextResponse.redirect(target, 307);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/protected/:path*"],
};
