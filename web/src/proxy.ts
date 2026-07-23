import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { SESSION_COOKIE_NAME } from "@/server/auth/cookie-policy";

const PROTECTED_PREFIXES = [
  "/protected",
  "/overview",
  "/farms",
  "/work",
  "/inventory",
  "/costs",
  "/crop-health",
  "/data-quality",
  "/administration"
];

export function proxy(request: NextRequest) {
  const configuredHosts = new Set(
    (process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS ?? "")
      .split(",")
      .map((host) => host.trim().toLowerCase())
      .filter(Boolean)
  );
  const host = request.headers.get("host")?.toLowerCase();
  if (!host || !configuredHosts.has(host)) {
    return new NextResponse("Invalid request host", { status: 400 });
  }
  if (
    PROTECTED_PREFIXES.some((prefix) =>
      request.nextUrl.pathname.startsWith(prefix)
    ) &&
    !request.cookies.has(SESSION_COOKIE_NAME)
  ) {
    const target = new URL("/login", request.url);
    target.searchParams.set("returnTo", request.nextUrl.pathname);
    return NextResponse.redirect(target, 307);
  }
  return NextResponse.next();
}

export const config = {
  matcher: [
    "/protected/:path*",
    "/overview/:path*",
    "/farms/:path*",
    "/work/:path*",
    "/inventory/:path*",
    "/costs/:path*",
    "/crop-health/:path*",
    "/data-quality/:path*",
    "/administration/:path*"
  ]
};
