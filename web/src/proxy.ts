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
  const nonce = crypto.randomUUID().replaceAll("-", "");
  const contentSecurityPolicy = buildContentSecurityPolicy(nonce);
  const configuredHosts = new Set(
    (process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS ?? "")
      .split(",")
      .map((host) => host.trim().toLowerCase())
      .filter(Boolean)
  );
  const host = request.headers.get("host")?.toLowerCase();
  if (!host || !configuredHosts.has(host)) {
    return withContentSecurityPolicy(
      new NextResponse("Invalid request host", { status: 400 }),
      contentSecurityPolicy
    );
  }
  if (
    PROTECTED_PREFIXES.some((prefix) =>
      request.nextUrl.pathname.startsWith(prefix)
    ) &&
    !request.cookies.has(SESSION_COOKIE_NAME)
  ) {
    const target = new URL("/login", request.url);
    target.searchParams.set("returnTo", request.nextUrl.pathname);
    return withContentSecurityPolicy(
      NextResponse.redirect(target, 307),
      contentSecurityPolicy
    );
  }
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", contentSecurityPolicy);
  return withContentSecurityPolicy(
    NextResponse.next({ request: { headers: requestHeaders } }),
    contentSecurityPolicy
  );
}

export const config = {
  matcher: [
    {
      source:
        "/((?!api/health|_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" }
      ]
    }
  ]
};

function buildContentSecurityPolicy(nonce: string): string {
  return [
    "default-src 'self'",
    "base-uri 'self'",
    "connect-src 'self'",
    "font-src 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "img-src 'self' data:",
    "object-src 'none'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'`,
    `style-src 'self' 'nonce-${nonce}'`,
    "worker-src 'self'",
    "require-trusted-types-for 'script'"
  ].join("; ");
}

function withContentSecurityPolicy(
  response: NextResponse,
  policy: string
): NextResponse {
  response.headers.set("Content-Security-Policy", policy);
  return response;
}
