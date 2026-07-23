import { AuthError } from "./auth-errors.ts";

export type AuthEnvironment = Readonly<{
  allowedHost: string;
  baseUrl: URL;
  callbackUrl: URL;
  clientId: string;
  clientSecret: string;
  databaseUrl: string;
  encryptionKey: Buffer;
  issuer: URL;
  keyId: string;
  sessionLifetimeSeconds: number;
}>;

const CALLBACK_PATH = "/api/auth/callback/keycloak";

function required(source: NodeJS.ProcessEnv, name: string): string {
  const value = source[name];
  if (!value) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function positiveInteger(value: string | undefined, fallback: number): number {
  if (!value) return fallback;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error("AUTH_SPIKE_SESSION_LIFETIME_SECONDS must be a positive integer");
  }
  return parsed;
}

export function loadAuthEnvironment(source: NodeJS.ProcessEnv = process.env): AuthEnvironment {
  const baseUrl = new URL(required(source, "AUTH_SPIKE_BASE_URL"));
  const callbackUrl = new URL(required(source, "AUTH_SPIKE_CALLBACK_URL"));
  const issuer = new URL(required(source, "AUTH_SPIKE_OIDC_ISSUER"));
  const allowedHost = required(source, "AUTH_SPIKE_ALLOWED_HOST");
  const encryptionKey = Buffer.from(
    required(source, "AUTH_SPIKE_SESSION_ENCRYPTION_KEY_BASE64"),
    "base64",
  );

  if (baseUrl.pathname !== "/" || baseUrl.search || baseUrl.hash) {
    throw new Error("AUTH_SPIKE_BASE_URL must be an origin without a path");
  }
  if (baseUrl.host !== allowedHost || baseUrl.hostname !== "localhost") {
    throw new Error("The spike accepts only its exact localhost base URL and host");
  }
  if (callbackUrl.origin !== baseUrl.origin || callbackUrl.pathname !== CALLBACK_PATH) {
    throw new Error(`AUTH_SPIKE_CALLBACK_URL must be ${baseUrl.origin}${CALLBACK_PATH}`);
  }
  if (issuer.hostname !== "localhost") {
    throw new Error("The disposable demo issuer must be loopback-only");
  }
  if (encryptionKey.length !== 32) {
    throw new Error("AUTH_SPIKE_SESSION_ENCRYPTION_KEY_BASE64 must decode to 32 bytes");
  }

  return {
    allowedHost,
    baseUrl,
    callbackUrl,
    clientId: required(source, "AUTH_SPIKE_OIDC_CLIENT_ID"),
    clientSecret: required(source, "AUTH_SPIKE_OIDC_CLIENT_SECRET"),
    databaseUrl: required(source, "AUTH_SPIKE_DATABASE_URL"),
    encryptionKey,
    issuer,
    keyId: required(source, "AUTH_SPIKE_TOKEN_KEY_ID"),
    sessionLifetimeSeconds: positiveInteger(source.AUTH_SPIKE_SESSION_LIFETIME_SECONDS, 28800),
  };
}

export function assertTrustedRequest(request: Request, env: AuthEnvironment): URL {
  const host = request.headers.get("host");
  if (host !== env.allowedHost) {
    throw new AuthError("invalid_host", 400, "Invalid request host.");
  }
  // Forwarded metadata is deliberately ignored. Trust comes only from Host and
  // the configured absolute URL, including when Next adds forwarding headers.
  const url = new URL(request.url);
  if (url.origin !== env.baseUrl.origin) {
    throw new AuthError("invalid_host", 400, "Invalid request origin.");
  }
  return url;
}

export function assertSameOriginMutation(request: Request, env: AuthEnvironment): void {
  const origin = request.headers.get("origin");
  if (origin !== env.baseUrl.origin) {
    throw new AuthError("invalid_request", 403, "Cross-origin mutation rejected.");
  }
}
