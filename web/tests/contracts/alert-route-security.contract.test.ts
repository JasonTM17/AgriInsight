import { randomUUID } from "node:crypto";

import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { POST as acknowledgeAlert } from "@/app/api/realtime/alerts/[alertId]/acknowledgements/route";
import { GET as getAlerts } from "@/app/api/realtime/alerts/route";
import { getAuthorizationContext } from "@/server/auth/authorization-context";
import { AuthError } from "@/server/auth/auth-error";
import {
  CSRF_COOKIE_NAME,
  SESSION_COOKIE_NAME
} from "@/server/auth/cookie-policy";
import { createCsrfToken } from "@/server/auth/csrf";
import {
  executeAllowedMutation,
  executeAllowedOperation
} from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

vi.mock("@/server/auth/authorization-context", () => ({
  getAuthorizationContext: vi.fn()
}));
vi.mock("@/server/bff/upstream-client", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/server/bff/upstream-client")>();
  return {
    ...original,
    executeAllowedMutation: vi.fn(),
    executeAllowedOperation: vi.fn()
  };
});

const alertId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
const eventId = "4fc03f21-71ee-46dc-a271-8d67ad4369c5";
const sessionToken = "opaque-alert-session";
const csrfKey = Buffer.alloc(32, 7);
const env = {
  allowedHosts: new Set(["app.example.test"]),
  analyticsBaseUrl: new URL("https://analytics.example.test"),
  backendBaseUrl: new URL("https://backend.example.test"),
  baseUrl: new URL("https://app.example.test"),
  callbackUrl: new URL("https://app.example.test/api/auth/callback"),
  clientId: "web-test",
  clientSecret: "test-only",
  csrfKey,
  databaseUrl: "postgresql://test.invalid/session",
  encryptionKey: Buffer.alloc(32, 8),
  issuer: new URL("https://issuer.example.test"),
  keyId: "test-v1",
  previousEncryptionKeys: new Map(),
  sessionLifetimeSeconds: 28_800,
  trustForwardedHeaders: false
} satisfies WebEnvironment;
const requireSession = vi.fn();

type RuntimeGlobal = typeof globalThis & {
  __agriInsightWebAuthRuntime?: unknown;
};

describe("realtime alert BFF trust boundaries", () => {
  beforeEach(() => {
    requireSession.mockReset();
    requireSession.mockResolvedValue({ accessToken: "server-token" });
    vi.mocked(getAuthorizationContext).mockReset();
    vi.mocked(getAuthorizationContext).mockResolvedValue(
      identity(["REALTIME_ALERT_READ", "REALTIME_ALERT_ACKNOWLEDGE"]) as never
    );
    vi.mocked(executeAllowedOperation).mockReset();
    vi.mocked(executeAllowedOperation).mockResolvedValue(
      Response.json(feed())
    );
    vi.mocked(executeAllowedMutation).mockReset();
    vi.mocked(executeAllowedMutation).mockResolvedValue(
      Response.json(alert(true))
    );
    (globalThis as RuntimeGlobal).__agriInsightWebAuthRuntime = {
      auth: { requireSession },
      env
    };
  });

  afterEach(() => {
    delete (globalThis as RuntimeGlobal).__agriInsightWebAuthRuntime;
  });

  it("rejects every GET query key before session and upstream work", async () => {
    const response = await getAlerts(
      readRequest("/api/realtime/alerts?tenantId=unsafe")
    );

    expect(response.status).toBe(400);
    expect(requireSession).not.toHaveBeenCalled();
    expect(executeAllowedOperation).not.toHaveBeenCalled();
  });

  it("forwards a tokenless fixed GET with caller cancellation", async () => {
    const request = readRequest("/api/realtime/alerts");
    const response = await getAlerts(request);
    const body = await response.text();

    expect(response.status).toBe(200);
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    expect(response.headers.get("X-Correlation-Id")).toMatch(
      /^[0-9a-f-]{36}$/i
    );
    expect(body).not.toContain("server-token");
    expect(executeAllowedOperation).toHaveBeenCalledWith(
      env,
      "realtimeAlerts",
      "server-token",
      expect.any(String),
      {},
      {},
      request.signal
    );
  });

  it.each([
    ["foreign Origin", { Origin: "https://evil.example" }, 403],
    ["invalid Host", { Host: "untrusted.example" }, 400],
    ["missing CSRF", { "X-AgriInsight-Csrf": "" }, 403]
  ])("rejects POST %s before session work", async (_case, headers, status) => {
    const response = await post(
      mutationRequest("/api/realtime/alerts/id/acknowledgements", {}, headers)
    );

    expect(response.status).toBe(status);
    expect(requireSession).not.toHaveBeenCalled();
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects an expired opaque session before idempotency or upstream work", async () => {
    requireSession.mockRejectedValueOnce(
      new AuthError("invalid_session", 401, "Phiên đã hết hạn.")
    );

    const response = await post(
      mutationRequest("/api/realtime/alerts/id/acknowledgements", {})
    );

    expect(response.status).toBe(401);
    expect(getAuthorizationContext).not.toHaveBeenCalled();
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("validates idempotency after session and before permission or body work", async () => {
    const response = await post(
      mutationRequest(
        "/api/realtime/alerts/id/acknowledgements",
        { tenantId: "would-be-invalid" },
        { "Idempotency-Key": "" }
      )
    );

    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({
      code: "invalid_idempotency_key"
    });
    expect(requireSession).toHaveBeenCalledWith(sessionToken);
    expect(getAuthorizationContext).not.toHaveBeenCalled();
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects POST query keys after idempotency and before permission or body work", async () => {
    const response = await post(
      mutationRequest(
        `/api/realtime/alerts/${alertId}/acknowledgements?profileId=unsafe`,
        {}
      )
    );

    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({ code: "unexpected_query" });
    expect(requireSession).toHaveBeenCalledWith(sessionToken);
    expect(getAuthorizationContext).not.toHaveBeenCalled();
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it.each([
    ["non-JSON", "text/plain", "not-json", 415],
    ["malformed JSON", "application/json", "{", 400],
    ["null JSON", "application/json", "null", 400],
    ["array JSON", "application/json", "[]", 400],
    ["extra field", "application/json", "{\"profileId\":\"unsafe\"}", 400],
    [
      "oversized JSON",
      "application/json",
      JSON.stringify({ note: "x".repeat(65 * 1024) }),
      413
    ]
  ])("rejects %s acknowledgement bodies", async (
    _case,
    contentType,
    body,
    status
  ) => {
    const response = await post(
      rawMutationRequest(
        "/api/realtime/alerts/id/acknowledgements",
        body,
        { "Content-Type": contentType }
      )
    );

    expect(response.status).toBe(status);
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects an invalid alert UUID after validating the exact empty body", async () => {
    const response = await acknowledgeAlert(
      mutationRequest("/api/realtime/alerts/not-a-uuid/acknowledgements", {}),
      { params: Promise.resolve({ alertId: "not-a-uuid" }) }
    );

    expect(response.status).toBe(400);
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it.each([
    ["read", ["REALTIME_ALERT_ACKNOWLEDGE"], "GET"],
    ["acknowledgement", ["REALTIME_ALERT_READ"], "POST"]
  ])("enforces the dedicated %s permission", async (_case, permissions, method) => {
    vi.mocked(getAuthorizationContext).mockResolvedValueOnce(
      identity(permissions) as never
    );

    const response = method === "GET"
      ? await getAlerts(readRequest("/api/realtime/alerts"))
      : await post(mutationRequest(
          `/api/realtime/alerts/${alertId}/acknowledgements`,
          {}
        ));

    expect(response.status).toBe(403);
    expect(executeAllowedOperation).not.toHaveBeenCalled();
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("posts exact empty JSON with server session credentials and cancellation", async () => {
    const request = mutationRequest(
      `/api/realtime/alerts/${alertId}/acknowledgements`,
      {}
    );
    const response = await acknowledgeAlert(request, {
      params: Promise.resolve({ alertId })
    });

    expect(response.status).toBe(200);
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    expect(response.headers.get("ETag")).toBeNull();
    expect(executeAllowedMutation).toHaveBeenCalledWith(
      env,
      "realtimeAlertAcknowledge",
      "server-token",
      expect.stringMatching(/^[0-9a-f-]{36}$/i),
      expect.any(String),
      {},
      { id: alertId },
      undefined,
      request.signal
    );
  });

  it("rejects malformed successful feed and acknowledgement payloads", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json({ ...feed(), items: Array.from({ length: 51 }, () => alert(false)) })
    );
    const feedResponse = await getAlerts(readRequest("/api/realtime/alerts"));
    expect(feedResponse.status).toBe(502);
    expect(await feedResponse.json()).toMatchObject({
      code: "invalid_upstream_response"
    });

    vi.mocked(executeAllowedMutation).mockResolvedValueOnce(
      Response.json({ ...alert(true), acknowledgedAt: null })
    );
    const acknowledgementResponse = await post(
      mutationRequest("/api/realtime/alerts/id/acknowledgements", {})
    );
    expect(acknowledgementResponse.status).toBe(502);
  });

  it.each([401, 403, 404, 409])(
    "sanitizes upstream %s bodies while preserving the safe status",
    async (status) => {
      vi.mocked(executeAllowedMutation).mockResolvedValueOnce(
        Response.json(
          { detail: "tenant profile and provider secret must not escape" },
          { status }
        )
      );
      const response = await post(
        mutationRequest("/api/realtime/alerts/id/acknowledgements", {})
      );
      const body = await response.text();

      expect(response.status).toBe(status);
      expect(body).not.toContain("provider secret");
      expect(body).not.toContain("tenant profile");
      expect(response.headers.get("X-Correlation-Id")).toMatch(
        /^[0-9a-f-]{36}$/i
      );
    }
  );

  it.each([404, 409])(
    "does not expose acknowledgement-only upstream %s statuses on the feed",
    async (status) => {
      vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
        Response.json({ detail: "unexpected feed failure" }, { status })
      );

      const response = await getAlerts(readRequest("/api/realtime/alerts"));

      expect(response.status).toBe(502);
      expect(await response.json()).toMatchObject({
        code: "upstream_unavailable"
      });
    }
  );
});

async function post(request: NextRequest) {
  return acknowledgeAlert(request, {
    params: Promise.resolve({ alertId })
  });
}

function identity(permissions: readonly string[]) {
  return {
    assurance: "mfa",
    displayName: "Realtime Operator",
    email: null,
    permissions: new Set(permissions),
    profileId: randomUUID(),
    roles: new Set(["EXECUTIVE"]),
    tenantCode: "demo",
    tenantId: randomUUID()
  };
}

function feed() {
  return {
    generatedAt: "2027-09-01T03:00:00Z",
    hasMore: false,
    items: [alert(false)],
    limit: 50
  };
}

function alert(acknowledged: boolean) {
  return {
    acknowledged,
    acknowledgedAt: acknowledged ? "2027-09-01T02:59:55Z" : null,
    ageSeconds: 30,
    evidence: {
      id: eventId,
      type: "OPERATIONAL_EVENT"
    },
    id: alertId,
    lastEvaluatedAt: "2027-09-01T03:00:00Z",
    lastObservedAt: "2027-09-01T02:59:30Z",
    openedAt: "2027-09-01T02:58:00Z",
    policy: "REALTIME_DLT_RECORD",
    severity: "CRITICAL",
    source: "realtime_operational",
    sourceOccurredAt: "2027-09-01T02:58:00Z",
    state: "OPEN"
  };
}

function readRequest(path: string, host = "app.example.test"): NextRequest {
  return new NextRequest(new URL(path, env.baseUrl), {
    headers: {
      Cookie: `${SESSION_COOKIE_NAME}=${sessionToken}`,
      Host: host
    }
  });
}

function mutationRequest(
  path: string,
  payload: unknown,
  headers: Readonly<Record<string, string>> = {}
): NextRequest {
  return rawMutationRequest(path, JSON.stringify(payload), headers);
}

function rawMutationRequest(
  path: string,
  body: string,
  headerOverrides: Readonly<Record<string, string>> = {}
): NextRequest {
  const csrf = createCsrfToken(sessionToken, csrfKey);
  const headers = new Headers({
    "Content-Type": "application/json",
    Cookie:
      `${SESSION_COOKIE_NAME}=${sessionToken}; `
      + `${CSRF_COOKIE_NAME}=${csrf}`,
    Host: "app.example.test",
    "Idempotency-Key": randomUUID(),
    Origin: env.baseUrl.origin,
    "X-AgriInsight-Csrf": csrf
  });
  for (const [name, value] of Object.entries(headerOverrides)) {
    if (value) headers.set(name, value);
    else headers.delete(name);
  }
  return new NextRequest(new URL(path, env.baseUrl), {
    method: "POST",
    body,
    headers
  });
}
