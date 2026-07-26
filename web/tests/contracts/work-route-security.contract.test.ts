import { randomUUID } from "node:crypto";

import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { POST as appendLog } from "@/app/api/work/activities/[activityId]/logs/route";
import { POST as correctLog } from "@/app/api/work/activities/[activityId]/logs/[logId]/corrections/route";
import { AuthError } from "@/server/auth/auth-error";
import {
  CSRF_COOKIE_NAME,
  SESSION_COOKIE_NAME
} from "@/server/auth/cookie-policy";
import { createCsrfToken } from "@/server/auth/csrf";
import { executeAllowedMutation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

vi.mock("@/server/bff/upstream-client", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/server/bff/upstream-client")>();
  return { ...original, executeAllowedMutation: vi.fn() };
});

const activityId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
const employeeId = "4fc03f21-71ee-46dc-a271-8d67ad4369c5";
const logId = "5ad14032-82ff-47ed-b382-9e78be547ad6";
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

const workRoutes = [
  {
    name: "append",
    invoke: (request: NextRequest) =>
      appendLog(request, { params: Promise.resolve({ activityId }) })
  },
  {
    name: "correction",
    invoke: (request: NextRequest) =>
      correctLog(request, {
        params: Promise.resolve({ activityId, logId })
      })
  }
] as const;

describe("work POST route security", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedMutation).mockReset();
    requireSession.mockReset();
    requireSession.mockResolvedValue({ accessToken: "server-token" });
    (globalThis as RuntimeGlobal).__agriInsightWebAuthRuntime = {
      auth: { requireSession },
      env
    };
  });

  afterEach(() => {
    delete (globalThis as RuntimeGlobal).__agriInsightWebAuthRuntime;
  });

  it("rejects a foreign origin before session or upstream work", async () => {
    const response = await appendLog(
      workRequest("/api/work/activities/id/logs", {
        Origin: "https://evil.example"
      }),
      { params: Promise.resolve({ activityId }) }
    );

    expect(response.status).toBe(403);
    expect(requireSession).not.toHaveBeenCalled();
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it.each(workRoutes)(
    "rejects an invalid Host on the $name route before session work",
    async ({ invoke }) => {
      const response = await invoke(
        workRequest("/api/work/security-check", {
          Host: "untrusted.example"
        })
      );

      expect(response.status).toBe(400);
      expect(requireSession).not.toHaveBeenCalled();
      expect(executeAllowedMutation).not.toHaveBeenCalled();
    }
  );

  it.each(workRoutes)(
    "rejects a missing CSRF header on the $name route",
    async ({ invoke }) => {
      const response = await invoke(
        workRequest("/api/work/security-check", {
          "X-AgriInsight-Csrf": ""
        })
      );

      expect(response.status).toBe(403);
      expect(requireSession).not.toHaveBeenCalled();
      expect(executeAllowedMutation).not.toHaveBeenCalled();
    }
  );

  it.each(workRoutes)(
    "rejects a mismatched CSRF token on the $name route",
    async ({ invoke }) => {
      const response = await invoke(
        workRequest("/api/work/security-check", {
          "X-AgriInsight-Csrf": "mismatched-token"
        })
      );

      expect(response.status).toBe(403);
      expect(requireSession).not.toHaveBeenCalled();
      expect(executeAllowedMutation).not.toHaveBeenCalled();
    }
  );

  it("rejects missing idempotency after authenticating the session", async () => {
    const response = await appendLog(
      workRequest("/api/work/activities/id/logs", {
        "Idempotency-Key": ""
      }),
      { params: Promise.resolve({ activityId }) }
    );

    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({
      code: "invalid_idempotency_key"
    });
    expect(requireSession).toHaveBeenCalledWith(sessionToken);
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects an expired session without calling the backend", async () => {
    requireSession.mockRejectedValueOnce(
      new AuthError("invalid_session", 401, "Phiên đã hết hạn.")
    );

    const response = await appendLog(
      workRequest("/api/work/activities/id/logs"),
      { params: Promise.resolve({ activityId }) }
    );

    expect(response.status).toBe(401);
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects non-JSON content before parsing the command", async () => {
    const response = await appendLog(
      workRequest(
        "/api/work/activities/id/logs",
        { "Content-Type": "text/plain" },
        "not-json"
      ),
      { params: Promise.resolve({ activityId }) }
    );

    expect(response.status).toBe(415);
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it.each(workRoutes)(
    "rejects malformed JSON on the $name route",
    async ({ invoke }) => {
      const response = await invoke(
        workRequest("/api/work/security-check", {}, "{")
      );

      expect(response.status).toBe(400);
      expect(await response.json()).toMatchObject({ code: "invalid_json" });
      expect(executeAllowedMutation).not.toHaveBeenCalled();
    }
  );

  it.each(workRoutes)(
    "rejects an oversized streamed body on the $name route",
    async ({ invoke }) => {
      const response = await invoke(
        workRequest(
          "/api/work/security-check",
          {},
          JSON.stringify({ notes: "x".repeat(65 * 1024) })
        )
      );

      expect(response.status).toBe(413);
      expect(await response.json()).toMatchObject({
        code: "request_too_large"
      });
      expect(executeAllowedMutation).not.toHaveBeenCalled();
    }
  );

  it("rejects an invalid correction path before upstream work", async () => {
    const response = await correctLog(
      workRequest(
        "/api/work/activities/id/logs/id/corrections",
        {},
        JSON.stringify(correctionPayload())
      ),
      {
        params: Promise.resolve({
          activityId,
          logId: "not-a-uuid"
        })
      }
    );

    expect(response.status).toBe(400);
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it.each([403, 404, 409])(
    "sanitizes an upstream %s without relaying its body",
    async (status) => {
      vi.mocked(executeAllowedMutation).mockResolvedValueOnce(
        Response.json(
          { detail: "provider secret should not escape" },
          { status }
        )
      );
      const response = await correctLog(
        workRequest(
          "/api/work/activities/id/logs/id/corrections",
          {},
          JSON.stringify(correctionPayload())
        ),
        { params: Promise.resolve({ activityId, logId }) }
      );
      const body = await response.text();

      expect(response.status).toBe(status);
      expect(body).not.toContain("provider secret");
      expect(response.headers.get("X-Correlation-Id")).toMatch(
        /^[0-9a-f-]{36}$/i
      );
    }
  );
});

function workRequest(
  path: string,
  headerOverrides: Readonly<Record<string, string>> = {},
  body: string = JSON.stringify({
    employeeId,
    occurredAt: "2026-07-26T01:00:00Z",
    notes: "Đã tưới đủ"
  })
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

function correctionPayload() {
  return {
    correctionKind: "REPLACE",
    correctionReason: "Đối chiếu lại ghi chú",
    occurredAt: "2026-07-26T01:05:00Z",
    notes: "Đã đối chiếu"
  };
}
