import { timingSafeEqual } from "node:crypto";

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
  if (!hasTrustedHost(request, configuredHosts)) {
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
    target.searchParams.set(
      "returnTo",
      `${request.nextUrl.pathname}${request.nextUrl.search}`
    );
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
    "/((?!api/health|_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml).*)"
  ]
};

function hasTrustedHost(
  request: NextRequest,
  configuredHosts: ReadonlySet<string>
): boolean {
  const host = request.headers.get("host")?.toLowerCase();
  if (!host || !configuredHosts.has(host)) return false;
  const forwardedHost = request.headers.get("x-forwarded-host")?.toLowerCase();
  const forwardedProto = request.headers.get("x-forwarded-proto")?.toLowerCase();
  const trustsForwarded = process.env.AGRIINSIGHT_WEB_TRUST_FORWARDED_HEADERS === "true";
  if (!trustsForwarded) {
    try {
      const baseUrl = new URL(process.env.AGRIINSIGHT_WEB_BASE_URL ?? "");
      return (
        host === baseUrl.host.toLowerCase() &&
        (!forwardedHost || forwardedHost === host) &&
        (!forwardedProto ||
          forwardedProto === baseUrl.protocol.slice(0, -1))
      );
    } catch {
      return false;
    }
  }
  try {
    const baseUrl = new URL(process.env.AGRIINSIGHT_WEB_BASE_URL ?? "");
    const expectedKey = Buffer.from(
      process.env.AGRIINSIGHT_WEB_TRUSTED_PROXY_KEY_BASE64 ?? "",
      "base64"
    );
    const suppliedKey = Buffer.from(
      request.headers.get("x-agriinsight-proxy-attestation") ?? "",
      "base64"
    );
    return (
      expectedKey.length === 32 &&
      suppliedKey.length === expectedKey.length &&
      timingSafeEqual(suppliedKey, expectedKey) &&
      forwardedHost === baseUrl.host.toLowerCase() &&
      forwardedProto === baseUrl.protocol.slice(0, -1)
    );
  } catch {
    return false;
  }
}

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
