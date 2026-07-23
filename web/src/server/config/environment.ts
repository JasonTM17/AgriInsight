import "server-only";

import { AuthError } from "@/server/auth/auth-error";

export type WebEnvironment = Readonly<{
  allowedHosts: ReadonlySet<string>;
  analyticsBaseUrl: URL;
  backendBaseUrl: URL;
  baseUrl: URL;
  callbackUrl: URL;
  clientId: string;
  clientSecret: string;
  csrfKey: Buffer;
  databaseUrl: string;
  encryptionKey: Buffer;
  issuer: URL;
  keyId: string;
  sessionLifetimeSeconds: number;
  trustForwardedHeaders: boolean;
}>;

const CALLBACK_PATH = "/api/auth/callback";

function required(source: NodeJS.ProcessEnv, name: string): string {
  const value = source[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function parseBoolean(value: string | undefined, name: string): boolean {
  if (value === undefined || value === "") return false;
  if (value === "true") return true;
  if (value === "false") return false;
  throw new Error(`${name} must be true or false`);
}

function parsePositiveInteger(value: string | undefined, fallback: number): number {
  if (!value) return fallback;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error("AGRIINSIGHT_WEB_SESSION_LIFETIME_SECONDS must be positive");
  }
  return parsed;
}

function parseKey(source: NodeJS.ProcessEnv, name: string): Buffer {
  const key = Buffer.from(required(source, name), "base64");
  if (key.length !== 32) throw new Error(`${name} must decode to 32 bytes`);
  return key;
}

function parseServiceUrl(value: string, name: string): URL {
  const url = new URL(value);
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password) {
    throw new Error(`${name} must be an HTTP(S) URL without credentials`);
  }
  if (url.pathname !== "/" || url.search || url.hash) {
    throw new Error(`${name} must be an origin without path, query, or fragment`);
  }
  return url;
}

export function loadWebEnvironment(
  source: NodeJS.ProcessEnv = process.env
): WebEnvironment {
  const baseUrl = parseServiceUrl(
    required(source, "AGRIINSIGHT_WEB_BASE_URL"),
    "AGRIINSIGHT_WEB_BASE_URL"
  );
  const issuer = new URL(required(source, "AGRIINSIGHT_WEB_OIDC_ISSUER"));
  const isLoopbackIssuer = ["localhost", "127.0.0.1", "::1"].includes(issuer.hostname);
  if (issuer.protocol !== "https:" && !(issuer.protocol === "http:" && isLoopbackIssuer)) {
    throw new Error("OIDC issuer must use HTTPS except for loopback development");
  }
  const allowedHosts = new Set(
    required(source, "AGRIINSIGHT_WEB_ALLOWED_HOSTS")
      .split(",")
      .map((host) => host.trim().toLowerCase())
      .filter(Boolean)
  );
  if (!allowedHosts.has(baseUrl.host.toLowerCase())) {
    throw new Error("AGRIINSIGHT_WEB_ALLOWED_HOSTS must include the configured base host");
  }

  return {
    allowedHosts,
    analyticsBaseUrl: parseServiceUrl(
      required(source, "AGRIINSIGHT_ANALYTICS_BASE_URL"),
      "AGRIINSIGHT_ANALYTICS_BASE_URL"
    ),
    backendBaseUrl: parseServiceUrl(
      required(source, "AGRIINSIGHT_BACKEND_BASE_URL"),
      "AGRIINSIGHT_BACKEND_BASE_URL"
    ),
    baseUrl,
    callbackUrl: new URL(CALLBACK_PATH, baseUrl),
    clientId: required(source, "AGRIINSIGHT_WEB_OIDC_CLIENT_ID"),
    clientSecret: required(source, "AGRIINSIGHT_WEB_OIDC_CLIENT_SECRET"),
    csrfKey: parseKey(source, "AGRIINSIGHT_WEB_CSRF_KEY_BASE64"),
    databaseUrl: required(source, "AGRIINSIGHT_WEB_SESSION_DATABASE_URL"),
    encryptionKey: parseKey(
      source,
      "AGRIINSIGHT_WEB_SESSION_ENCRYPTION_KEY_BASE64"
    ),
    issuer,
    keyId: required(source, "AGRIINSIGHT_WEB_TOKEN_KEY_ID"),
    sessionLifetimeSeconds: parsePositiveInteger(
      source.AGRIINSIGHT_WEB_SESSION_LIFETIME_SECONDS,
      28_800
    ),
    trustForwardedHeaders: parseBoolean(
      source.AGRIINSIGHT_WEB_TRUST_FORWARDED_HEADERS,
      "AGRIINSIGHT_WEB_TRUST_FORWARDED_HEADERS"
    )
  };
}

export function assertTrustedRequest(request: Request, env: WebEnvironment): URL {
  const host = request.headers.get("host")?.toLowerCase();
  const forwardedHost = request.headers.get("x-forwarded-host")?.toLowerCase();
  const forwardedProto = request.headers.get("x-forwarded-proto")?.toLowerCase();
  if (!host || !env.allowedHosts.has(host)) {
    throw new AuthError("invalid_host", 400, "Máy chủ yêu cầu không hợp lệ.");
  }
  if (!env.trustForwardedHeaders && (forwardedHost || forwardedProto)) {
    throw new AuthError("invalid_host", 400, "Forwarded headers are not trusted.");
  }
  if (env.trustForwardedHeaders) {
    if (
      forwardedHost !== env.baseUrl.host.toLowerCase() ||
      forwardedProto !== env.baseUrl.protocol.slice(0, -1)
    ) {
      throw new AuthError("invalid_host", 400, "Forwarded host is not allowed.");
    }
  } else if (
    host !== env.baseUrl.host.toLowerCase() ||
    new URL(request.url).origin !== env.baseUrl.origin
  ) {
    throw new AuthError("invalid_host", 400, "Nguồn yêu cầu không hợp lệ.");
  }
  return new URL(request.url);
}
