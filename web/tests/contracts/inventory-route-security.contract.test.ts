import { randomUUID } from "node:crypto";

import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { GET as getTransaction } from "@/app/api/inventory/transactions/[transactionId]/route";
import { POST as reverseTransaction } from "@/app/api/inventory/transactions/[transactionId]/reversals/route";
import { POST as postTransaction } from "@/app/api/inventory/transactions/route";
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

const warehouseId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
const materialId = "4fc03f21-71ee-46dc-a271-8d67ad4369c5";
const supplierId = "5ad14032-82ff-47ed-b382-9e78be547ad6";
const transactionId = "66050634-6a22-45c6-a896-5a83602caf45";
const profileId = "7935a09b-1f9f-4d08-a58a-a45bdd4e449d";
const sessionToken = "opaque-inventory-session";
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

describe("inventory route trust boundaries", () => {
  beforeEach(() => {
    requireSession.mockReset();
    requireSession.mockResolvedValue({ accessToken: "server-token" });
    vi.mocked(getAuthorizationContext).mockReset();
    vi.mocked(getAuthorizationContext).mockResolvedValue(
      identity(["INVENTORY_READ", "INVENTORY_MANAGE"]) as never
    );
    vi.mocked(executeAllowedOperation).mockReset();
    vi.mocked(executeAllowedOperation).mockImplementation(
      async (_env, operation) => {
        if (operation === "warehouseCatalog") {
          return Response.json({
            hasMore: false,
            items: [warehouse()],
            limit: 100,
            offset: 0
          });
        }
        if (operation === "inventoryTransactionById") {
          return Response.json(transaction(), {
            headers: { ETag: "\"7\"" }
          });
        }
        throw new Error(`Unexpected operation: ${operation}`);
      }
    );
    vi.mocked(executeAllowedMutation).mockReset();
    vi.mocked(executeAllowedMutation).mockResolvedValue(
      Response.json(transaction(), {
        status: 201,
        headers: { ETag: "\"8\"" }
      })
    );
    (globalThis as RuntimeGlobal).__agriInsightWebAuthRuntime = {
      auth: { requireSession },
      env
    };
  });

  afterEach(() => {
    delete (globalThis as RuntimeGlobal).__agriInsightWebAuthRuntime;
  });

  it("posts a scoped receipt with session-held credentials", async () => {
    const response = await postTransaction(
      mutationRequest(
        "/api/inventory/transactions",
        receiptPayload()
      )
    );

    expect(response.status).toBe(201);
    expect(response.headers.get("ETag")).toBe("\"8\"");
    expect(executeAllowedMutation).toHaveBeenCalledWith(
      env,
      "inventoryTransactionPost",
      "server-token",
      expect.stringMatching(/^[0-9a-f-]{36}$/i),
      expect.any(String),
      expect.objectContaining({
        kind: "RECEIPT",
        reasonCode: "WEB_RECEIPT_ENTRY",
        warehouseId
      }),
      {}
    );
  });

  it("rejects a foreign warehouse before the mutation upstream", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json({ hasMore: false, items: [], limit: 100, offset: 0 })
    );
    const response = await postTransaction(
      mutationRequest("/api/inventory/transactions", receiptPayload())
    );

    expect(response.status).toBe(403);
    expect(await response.json()).toMatchObject({ code: "scope_denied" });
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it.each([
    ["foreign Origin", { Origin: "https://evil.example" }, 403],
    ["invalid Host", { Host: "untrusted.example" }, 400],
    ["missing CSRF", { "X-AgriInsight-Csrf": "" }, 403],
    ["missing idempotency", { "Idempotency-Key": "" }, 400]
  ])("rejects %s before upstream mutation", async (_case, headers, status) => {
    const response = await postTransaction(
      mutationRequest(
        "/api/inventory/transactions",
        receiptPayload(),
        headers
      )
    );

    expect(response.status).toBe(status);
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects an expired session without reading an upstream catalog", async () => {
    requireSession.mockRejectedValueOnce(
      new AuthError("invalid_session", 401, "Phiên đã hết hạn.")
    );
    const response = await postTransaction(
      mutationRequest("/api/inventory/transactions", receiptPayload())
    );

    expect(response.status).toBe(401);
    expect(executeAllowedOperation).not.toHaveBeenCalled();
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects a read-only inventory persona", async () => {
    vi.mocked(getAuthorizationContext).mockResolvedValueOnce(
      identity(["INVENTORY_READ"]) as never
    );
    const response = await postTransaction(
      mutationRequest("/api/inventory/transactions", receiptPayload())
    );

    expect(response.status).toBe(403);
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it.each([
    ["non-JSON", "text/plain", "not-json", 415],
    ["malformed JSON", "application/json", "{", 400],
    [
      "oversized JSON",
      "application/json",
      JSON.stringify({ notes: "x".repeat(65 * 1024) }),
      413
    ]
  ])("rejects %s bodies", async (_case, contentType, body, status) => {
    const response = await postTransaction(
      rawMutationRequest("/api/inventory/transactions", body, {
        "Content-Type": contentType
      })
    );

    expect(response.status).toBe(status);
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("exposes an exact transaction ETag without a bearer token leak", async () => {
    const response = await getTransaction(
      readRequest(`/api/inventory/transactions/${transactionId}`),
      { params: Promise.resolve({ transactionId }) }
    );

    expect(response.status).toBe(200);
    expect(response.headers.get("ETag")).toBe("\"7\"");
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    expect(await response.json()).toMatchObject({ id: transactionId });
  });

  it.each([undefined, "W/\"7\"", "7", "\"99999999999999999999\""])(
    "rejects invalid reversal If-Match %s",
    async (ifMatch) => {
      const headers: Record<string, string> = {};
      if (ifMatch !== undefined) headers["If-Match"] = ifMatch;
      const response = await reverseTransaction(
        mutationRequest(
          `/api/inventory/transactions/${transactionId}/reversals`,
          reversalPayload(),
          headers
        ),
        { params: Promise.resolve({ transactionId }) }
      );

      expect(response.status).toBe(400);
      expect(executeAllowedMutation).not.toHaveBeenCalled();
    }
  );

  it("rejects a stale ETag after refetching the source transaction", async () => {
    const response = await reverseTransaction(
      mutationRequest(
        `/api/inventory/transactions/${transactionId}/reversals`,
        reversalPayload(),
        { "If-Match": "\"6\"" }
      ),
      { params: Promise.resolve({ transactionId }) }
    );

    expect(response.status).toBe(409);
    expect(executeAllowedOperation).toHaveBeenCalledWith(
      env,
      "inventoryTransactionById",
      "server-token",
      expect.any(String),
      {},
      { id: transactionId }
    );
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("forwards the raw refetched ETag on reversal", async () => {
    const response = await reverseTransaction(
      mutationRequest(
        `/api/inventory/transactions/${transactionId}/reversals`,
        reversalPayload(),
        { "If-Match": "\"7\"" }
      ),
      { params: Promise.resolve({ transactionId }) }
    );

    expect(response.status).toBe(201);
    expect(executeAllowedMutation).toHaveBeenCalledWith(
      env,
      "inventoryTransactionReversal",
      "server-token",
      expect.any(String),
      expect.any(String),
      {
        quantityBase: 1,
        reason: "Correct duplicate posting",
        reasonCode: "WEB_REVERSAL_ENTRY"
      },
      { id: transactionId },
      "\"7\""
    );
  });

  it("sanitizes an upstream denial body", async () => {
    vi.mocked(executeAllowedMutation).mockResolvedValueOnce(
      Response.json(
        { detail: "provider secret must not escape" },
        { status: 403 }
      )
    );
    const response = await postTransaction(
      mutationRequest("/api/inventory/transactions", receiptPayload())
    );
    const body = await response.text();

    expect(response.status).toBe(403);
    expect(body).not.toContain("provider secret");
  });
});

function identity(permissions: readonly string[]) {
  return {
    assurance: "mfa",
    displayName: "Inventory Manager",
    email: null,
    permissions: new Set(permissions),
    profileId,
    roles: new Set(["INVENTORY_MANAGER"]),
    tenantCode: "demo",
    tenantId: "a4b5d235-1d78-49ea-924f-a2f865c73238"
  };
}

function warehouse() {
  return {
    active: true,
    code: "WH-001",
    displayName: "Kho trung tâm",
    id: warehouseId,
    locationText: "Khu A",
    version: 1
  };
}

function transaction() {
  return {
    id: transactionId,
    kind: "RECEIPT",
    materialId,
    occurredAt: "2027-01-01T08:00:00Z",
    procurementEffectVnd: 1_250_000,
    quantityBase: 100,
    recordedByProfileId: profileId,
    signedQuantityEffect: 100,
    supplierId,
    unit: "KG",
    unitCostVnd: 12_500,
    version: 7,
    warehouseId
  };
}

function receiptPayload() {
  return {
    kind: "RECEIPT",
    warehouseId,
    materialId,
    supplierId,
    quantityBase: 100,
    unitCostVnd: 12_500,
    batchCode: "NPK-2027-01",
    expiryDate: "2027-12-31",
    occurredAt: "2027-01-01T08:00:00Z"
  };
}

function reversalPayload() {
  return {
    quantityBase: 1,
    reason: "Correct duplicate posting"
  };
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

function readRequest(path: string): NextRequest {
  return new NextRequest(new URL(path, env.baseUrl), {
    headers: {
      Cookie: `${SESSION_COOKIE_NAME}=${sessionToken}`,
      Host: "app.example.test"
    }
  });
}
