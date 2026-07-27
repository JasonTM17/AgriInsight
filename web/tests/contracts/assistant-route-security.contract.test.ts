import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { POST } from "@/app/api/assistant/query/route";
import { getAuthorizationContext } from "@/server/auth/authorization-context";
import {
  CSRF_COOKIE_NAME,
  SESSION_COOKIE_NAME
} from "@/server/auth/cookie-policy";
import { createCsrfToken } from "@/server/auth/csrf";
import { executeAllowedAnalyticsCommand } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

vi.mock("@/server/auth/authorization-context", () => ({
  getAuthorizationContext: vi.fn()
}));
vi.mock("@/server/bff/upstream-client", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/server/bff/upstream-client")>();
  return { ...original, executeAllowedAnalyticsCommand: vi.fn() };
});

const sessionToken = "opaque-session";
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

describe("assistant BFF route security", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedAnalyticsCommand).mockReset();
    vi.mocked(getAuthorizationContext).mockReset();
    requireSession.mockReset();
    requireSession.mockResolvedValue({ accessToken: "server-token" });
    vi.mocked(getAuthorizationContext).mockResolvedValue(identity());
    (globalThis as RuntimeGlobal).__agriInsightWebAuthRuntime = {
      auth: { requireSession },
      env
    };
  });

  afterEach(() => {
    delete (globalThis as RuntimeGlobal).__agriInsightWebAuthRuntime;
  });

  it("rejects foreign origins and missing CSRF before provider work", async () => {
    const foreign = await POST(assistantRequest({
      Origin: "https://evil.example"
    }));
    const missingCsrf = await POST(assistantRequest({
      "X-AgriInsight-Csrf": ""
    }));

    expect(foreign.status).toBe(403);
    expect(missingCsrf.status).toBe(403);
    expect(executeAllowedAnalyticsCommand).not.toHaveBeenCalled();
  });

  it("denies unsupported roles before forwarding the question", async () => {
    vi.mocked(getAuthorizationContext).mockResolvedValueOnce(identity(
      ["FARM_READ", "INVENTORY_READ"],
      ["SUPPLIER"]
    ));

    const response = await POST(assistantRequest());

    expect(response.status).toBe(403);
    expect(await response.json()).toMatchObject({ code: "scope_denied" });
    expect(executeAllowedAnalyticsCommand).not.toHaveBeenCalled();
  });

  it("rejects provider, tenant, and model fields from the browser", async () => {
    const response = await POST(assistantRequest({}, JSON.stringify({
      model: "deepseek-v4-pro",
      question: "Kho nào sắp thiếu?",
      tenantId: "other-tenant"
    })));

    expect(response.status).toBe(400);
    expect(executeAllowedAnalyticsCommand).not.toHaveBeenCalled();
  });

  it("forwards an exact validated query and validates the answer", async () => {
    vi.mocked(executeAllowedAnalyticsCommand).mockResolvedValueOnce(
      Response.json(validAnswer())
    );

    const response = await POST(assistantRequest());

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual(validAnswer());
    expect(executeAllowedAnalyticsCommand).toHaveBeenCalledWith(
      env,
      "analyticsAssistantQuery",
      "server-token",
      expect.stringMatching(/^[0-9a-f-]{36}$/i),
      { question: "Kho nào sắp thiếu vật tư?" },
      expect.any(AbortSignal)
    );
  });

  it.each([401, 403, 422, 429, 500, 503])(
    "sanitizes upstream %s bodies",
    async (status) => {
      vi.mocked(executeAllowedAnalyticsCommand).mockResolvedValueOnce(
        Response.json(
          { detail: "DeepSeek provider detail and secret should not escape" },
          { status }
        )
      );

      const response = await POST(assistantRequest());
      const body = await response.text();

      expect(body).not.toContain("DeepSeek provider detail");
      expect(response.headers.get("Cache-Control")).toBe("no-store");
      if (status === 429) {
        expect(response.status).toBe(429);
        expect(body).toContain("assistant_rate_limited");
      }
    }
  );
});

function assistantRequest(
  overrides: Readonly<Record<string, string>> = {},
  body = JSON.stringify({ question: "Kho nào sắp thiếu vật tư?" })
): NextRequest {
  const csrf = createCsrfToken(sessionToken, csrfKey);
  return new NextRequest("https://app.example.test/api/assistant/query", {
    method: "POST",
    body,
    headers: {
      "Content-Type": "application/json",
      Cookie:
        `${SESSION_COOKIE_NAME}=${sessionToken}; `
        + `${CSRF_COOKIE_NAME}=${csrf}`,
      Host: "app.example.test",
      Origin: env.baseUrl.origin,
      "X-AgriInsight-Csrf": csrf,
      ...overrides
    }
  });
}

function identity(
  permissions = ["FARM_READ"],
  roles = ["FARM_MANAGER"]
) {
  return {
    assurance: "mfa",
    displayName: "Quản lý nông trại",
    email: null,
    permissions: new Set(permissions),
    profileId: "3eb92f10-60dd-45cb-9160-7c569c3258b4",
    roles: new Set(roles),
    tenantCode: "tenant-a",
    tenantId: "4fc03f21-71ee-46dc-a271-8d67ad4369c5"
  };
}

function validAnswer() {
  return {
    answer: "Kho WH-01 cần theo dõi [inventory:wh-01:mat-01].",
    citations: [{
      asOf: "2026-07-27",
      evidenceId: "inventory:wh-01:mat-01",
      excerpt: "Số ngày cung ứng thấp hơn ngưỡng vận hành.",
      sourceType: "inventory",
      title: "Tồn kho MAT-01 · WH-01"
    }],
    status: "answered",
    usage: {
      completionTokens: 20,
      promptCacheHitTokens: 40,
      promptCacheMissTokens: 60,
      promptTokens: 100,
      totalTokens: 120
    }
  };
}
