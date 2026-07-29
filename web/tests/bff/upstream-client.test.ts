import { afterEach, describe, expect, it, vi } from "vitest";

import {
  UpstreamResponseError,
  boundedUpstreamFetch,
  boundedUpstreamStreamFetch
} from "@/server/bff/bounded-upstream-fetch";
import {
  executeAllowedFileOperation,
  executeAllowedAnalyticsCommand,
  executeAllowedMutation,
  executeAllowedOperation
} from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

const env = {
  analyticsBaseUrl: new URL("http://127.0.0.1:8081"),
  backendBaseUrl: new URL("http://127.0.0.1:8080")
} as WebEnvironment;

describe("bounded upstream client", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("injects bearer only into the fixed service and operation URL", async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request, init?: RequestInit) => {
        void input;
        void init;
        return new Response(JSON.stringify({ tenantId: "tenant" }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
        });
      }
    );
    vi.stubGlobal("fetch", fetchMock);
    await executeAllowedOperation(
      env,
      "currentUser",
      "server-held-token",
      "correlation-1"
    );
    const [input, init] = fetchMock.mock.calls[0]!;
    expect(String(input)).toBe("http://127.0.0.1:8080/api/v1/me");
    expect(init?.headers).toMatchObject({
      Authorization: "Bearer server-held-token",
      "X-Correlation-Id": "correlation-1"
    });
    expect(init?.cache).toBe("no-store");
    expect(init?.redirect).toBe("manual");
  });

  it("rejects upstream redirects before following them", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(null, {
          status: 302,
          headers: { Location: "http://metadata.invalid/latest" }
        })
      )
    );
    await expect(
      boundedUpstreamFetch("http://127.0.0.1:8080/api/v1/me", {
        method: "GET"
      })
    ).rejects.toBeInstanceOf(UpstreamResponseError);
  });

  it("interpolates only an exact UUID into an allowlisted resource path", async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request) => {
        void input;
        return Response.json({ id: "farm" });
      }
    );
    vi.stubGlobal("fetch", fetchMock);
    const farmId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
    await executeAllowedOperation(
      env,
      "farmById",
      "server-held-token",
      "correlation-1",
      {},
      { id: farmId }
    );
    expect(String(fetchMock.mock.calls[0]![0])).toBe(
      `http://127.0.0.1:8080/api/v1/farms/${farmId}`
    );
  });

  it("forwards contract-defined snake-case analytics query parameters", async () => {
    const fetchMock = vi.fn(async (input: string | URL | Request) => {
      void input;
      return Response.json({ payload: {} });
    });
    vi.stubGlobal("fetch", fetchMock);

    await executeAllowedOperation(
      env,
      "analyticsOverview",
      "server-held-token",
      "correlation-1",
      {
        date_preset: "all",
        farm_code: "FARM-001"
      }
    );

    expect(String(fetchMock.mock.calls[0]![0])).toBe(
      "http://127.0.0.1:8081/internal/v1/overview"
      + "?date_preset=all&farm_code=FARM-001"
    );
  });

  it("posts a bounded JSON body with only hardcoded mutation headers", async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request, init?: RequestInit) => {
        void input;
        void init;
        return Response.json({ id: "log" }, { status: 201 });
      }
    );
    vi.stubGlobal("fetch", fetchMock);
    const activityId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
    const body = {
      notes: "Irrigation completed",
      quantity: 12
    };

    await executeAllowedMutation(
      env,
      "activityLogAppend",
      "server-held-token",
      "correlation-1",
      "append-log-1",
      body,
      { id: activityId }
    );

    const [input, init] = fetchMock.mock.calls[0]!;
    expect(String(input)).toBe(
      `http://127.0.0.1:8080/api/v1/activities/${activityId}/logs`
    );
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify(body));
    expect(init?.headers).toEqual({
      Accept: "application/json",
      Authorization: "Bearer server-held-token",
      "Content-Type": "application/json",
      "Idempotency-Key": "append-log-1",
      "X-Correlation-Id": "correlation-1"
    });
    expect(init?.headers).not.toHaveProperty("If-Match");
    expect(init?.cache).toBe("no-store");
    expect(init?.redirect).toBe("manual");
  });

  it("posts assistant queries only to the fixed analytics endpoint", async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request, init?: RequestInit) => {
        void input;
        void init;
        return Response.json({
        answer: "Kho WH-01 cần theo dõi [inventory:wh-01:mat-01].",
        citations: [],
        status: "insufficient_evidence",
        usage: {
          completionTokens: 0,
          promptCacheHitTokens: 0,
          promptCacheMissTokens: 0,
          promptTokens: 0,
          totalTokens: 0
        }
        });
      }
    );
    vi.stubGlobal("fetch", fetchMock);
    const body = { question: "Kho nào cần theo dõi?" };

    await executeAllowedAnalyticsCommand(
      env,
      "analyticsAssistantQuery",
      "server-held-token",
      "correlation-assistant",
      body
    );

    const [input, init] = fetchMock.mock.calls[0]!;
    expect(String(input)).toBe(
      "http://127.0.0.1:8081/internal/v1/assistant/query"
    );
    expect(init?.body).toBe(JSON.stringify(body));
    expect(init?.headers).toEqual({
      Accept: "application/json",
      Authorization: "Bearer server-held-token",
      "Content-Type": "application/json",
      "X-Correlation-Id": "correlation-assistant"
    });
    expect(init?.headers).not.toHaveProperty("Idempotency-Key");
    expect(init?.redirect).toBe("manual");
  });

  it("propagates assistant client cancellation into the bounded fetch", async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request, init?: RequestInit) => {
        void input;
        void init;
        return Response.json({});
      }
    );
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();

    await executeAllowedAnalyticsCommand(
      env,
      "analyticsAssistantQuery",
      "server-held-token",
      "correlation-assistant",
      { question: "Kho nào cần theo dõi?" },
      controller.signal
    );

    const forwardedSignal = fetchMock.mock.calls[0]![1]?.signal;
    expect(forwardedSignal?.aborted).toBe(false);
    controller.abort();
    expect(forwardedSignal?.aborted).toBe(true);
  });

  it("forwards alert GET cancellation without accepting query controls", async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request, init?: RequestInit) => {
        void input;
        void init;
        return Response.json({});
      },
    );
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();

    await executeAllowedOperation(
      env,
      "realtimeAlerts",
      "server-held-token",
      "correlation-alert",
      {},
      {},
      controller.signal
    );

    const [input, init] = fetchMock.mock.calls[0]!;
    expect(String(input)).toBe(
      "http://127.0.0.1:8080/api/v1/realtime/alerts"
    );
    expect(init?.signal?.aborted).toBe(false);
    controller.abort();
    expect(init?.signal?.aborted).toBe(true);
    await expect(
      executeAllowedOperation(
        env,
        "realtimeAlerts",
        "server-held-token",
        "correlation-alert",
        { tenantId: "unsafe" }
      )
    ).rejects.toThrow("not allowlisted");
  });

  it("posts exact alert acknowledgement JSON and forwards cancellation", async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request, init?: RequestInit) => {
        void input;
        void init;
        return Response.json({});
      },
    );
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();
    const alertId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";

    await executeAllowedMutation(
      env,
      "realtimeAlertAcknowledge",
      "server-held-token",
      "correlation-alert",
      "ack-alert-1",
      {},
      { id: alertId },
      undefined,
      controller.signal
    );

    const [input, init] = fetchMock.mock.calls[0]!;
    expect(String(input)).toBe(
      `http://127.0.0.1:8080/api/v1/realtime/alerts/${alertId}/acknowledgements`
    );
    expect(init?.body).toBe("{}");
    expect(init?.signal?.aborted).toBe(false);
    controller.abort();
    expect(init?.signal?.aborted).toBe(true);
    expect(init?.headers).not.toHaveProperty("If-Match");
  });

  it("applies the two-megabyte response cap to the alert operation", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response("{}", {
          status: 200,
          headers: { "Content-Length": String(2 * 1024 * 1024 + 1) }
        })
      )
    );

    await expect(
      executeAllowedOperation(
        env,
        "realtimeAlerts",
        "server-held-token",
        "correlation-alert"
      )
    ).rejects.toThrow("byte limit");
  });

  it("interpolates both exact UUIDs into the correction POST path", async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request, init?: RequestInit) => {
        void input;
        void init;
        return Response.json({ id: "correction" });
      }
    );
    vi.stubGlobal("fetch", fetchMock);
    const activityId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
    const logId = "4fc03f21-71ee-46dc-a271-8d67ad4369c5";

    await executeAllowedMutation(
      env,
      "activityLogCorrection",
      "server-held-token",
      "correlation-1",
      "correct-log-1",
      { reason: "Corrected unit" },
      { id: activityId, logId }
    );

    expect(String(fetchMock.mock.calls[0]![0])).toBe(
      "http://127.0.0.1:8080/api/v1/activities/"
      + `${activityId}/logs/${logId}/corrections`
    );
    expect(fetchMock.mock.calls[0]![1]?.method).toBe("POST");
  });

  it("allows only a fixed role code in the admin role-revoke path", async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request) => {
        void input;
        return Response.json({ active: false, roleCode: "DATA_ANALYST" });
      }
    );
    vi.stubGlobal("fetch", fetchMock);
    const profileId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";

    await executeAllowedMutation(
      env,
      "adminRoleRevoke",
      "server-held-token",
      "correlation-1",
      "revoke-role-1",
      { reasonCode: "ADMIN_ROLE_REVOKE" },
      { id: profileId, roleCode: "DATA_ANALYST" },
      "\"2\""
    );

    expect(String(fetchMock.mock.calls[0]![0])).toBe(
      `http://127.0.0.1:8080/api/v1/users/${profileId}/roles/DATA_ANALYST/revoke`
    );
    await expect(
      executeAllowedMutation(
        env,
        "adminRoleRevoke",
        "server-held-token",
        "correlation-1",
        "revoke-role-2",
        { reasonCode: "ADMIN_ROLE_REVOKE" },
        { id: profileId, roleCode: "../audit-events" },
        "\"2\""
      )
    ).rejects.toThrow("path parameter");
  });

  it("forwards a validated If-Match only for inventory reversal", async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request, init?: RequestInit) => {
        void input;
        void init;
        return Response.json({ id: "reversal" }, { status: 201 });
      }
    );
    vi.stubGlobal("fetch", fetchMock);
    const transactionId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";

    await executeAllowedMutation(
      env,
      "inventoryTransactionReversal",
      "server-held-token",
      "correlation-1",
      "reverse-transaction-1",
      { quantityBase: 2, reason: "Correct duplicate posting" },
      { id: transactionId },
      "\"7\""
    );

    expect(String(fetchMock.mock.calls[0]![0])).toBe(
      `http://127.0.0.1:8080/api/v1/inventory/transactions/${transactionId}/reversals`
    );
    expect(fetchMock.mock.calls[0]![1]?.headers).toMatchObject({
      "Idempotency-Key": "reverse-transaction-1",
      "If-Match": "\"7\""
    });
  });

  it("rejects missing, malformed, or non-allowlisted If-Match before fetch", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const transactionId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";

    await expect(
      executeAllowedMutation(
        env,
        "inventoryTransactionReversal",
        "server-held-token",
        "correlation-1",
        "reverse-transaction-1",
        { quantityBase: 2, reason: "Correct duplicate posting" },
        { id: transactionId }
      )
    ).rejects.toThrow("If-Match");
    await expect(
      executeAllowedMutation(
        env,
        "inventoryTransactionReversal",
        "server-held-token",
        "correlation-1",
        "reverse-transaction-1",
        { quantityBase: 2, reason: "Correct duplicate posting" },
        { id: transactionId },
        "7"
      )
    ).rejects.toThrow("If-Match");
    await expect(
      executeAllowedMutation(
        env,
        "activityLogAppend",
        "server-held-token",
        "correlation-1",
        "append-log-1",
        { notes: "Irrigation completed" },
        { id: transactionId },
        "\"7\""
      )
    ).rejects.toThrow("not allowlisted");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects missing, extra, or unsafe resource path parameters", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const invalidParameters: ReadonlyArray<Readonly<Record<string, string>>> = [
      {},
      { id: "../admin" },
      {
        id: "3eb92f10-60dd-45cb-9160-7c569c3258b4",
        extra: "3eb92f10-60dd-45cb-9160-7c569c3258b4"
      }
    ];
    for (const pathParameters of invalidParameters) {
      await expect(
        executeAllowedOperation(
          env,
          "farmById",
          "server-held-token",
          "correlation-1",
          {},
          pathParameters
        )
      ).rejects.toThrow("path parameter");
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects missing, extra, or unsafe mutation path parameters", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const invalidParameters: ReadonlyArray<Readonly<Record<string, string>>> = [
      { id: "3eb92f10-60dd-45cb-9160-7c569c3258b4" },
      {
        id: "3eb92f10-60dd-45cb-9160-7c569c3258b4",
        logId: "../logs"
      },
      {
        id: "3eb92f10-60dd-45cb-9160-7c569c3258b4",
        logId: "4fc03f21-71ee-46dc-a271-8d67ad4369c5",
        extra: "5ad14032-82ff-47ed-b382-9e78be547ad6"
      }
    ];
    for (const pathParameters of invalidParameters) {
      await expect(
        executeAllowedMutation(
          env,
          "activityLogCorrection",
          "server-held-token",
          "correlation-1",
          "correct-log-1",
          { reason: "Corrected unit" },
          pathParameters
        )
      ).rejects.toThrow("path parameter");
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["missing", undefined],
    ["empty", ""],
    ["space-containing", "contains space"],
    ["non-visible", "line\nbreak"],
    ["non-ASCII", "café"],
    ["too-long", "x".repeat(201)]
  ])("rejects a %s idempotency key before fetch", async (_case, key) => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      executeAllowedMutation(
        env,
        "activityLogAppend",
        "server-held-token",
        "correlation-1",
        key as string,
        { notes: "Irrigation completed" },
        { id: "3eb92f10-60dd-45cb-9160-7c569c3258b4" }
      )
    ).rejects.toThrow("idempotency key");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects an oversized serialized request body before fetch", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      executeAllowedMutation(
        env,
        "activityLogAppend",
        "server-held-token",
        "correlation-1",
        "append-log-1",
        { notes: "x".repeat(64 * 1024) },
        { id: "3eb92f10-60dd-45cb-9160-7c569c3258b4" }
      )
    ).rejects.toThrow("byte limit");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects an upstream response declared above the two-megabyte cap", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response("{}", {
          status: 200,
          headers: { "Content-Length": String(2 * 1024 * 1024 + 1) }
        })
      )
    );
    await expect(
      boundedUpstreamFetch("http://127.0.0.1:8081/internal/v1/overview", {
        method: "GET"
      })
    ).rejects.toThrow("byte limit");
  });

  it("streams an allowlisted export without pre-buffering its body", async () => {
    let pulled = false;
    const fetchMock = vi.fn(
      async (input: string | URL | Request, init?: RequestInit) => {
        void input;
        void init;
        return new Response(
          new ReadableStream<Uint8Array>({
            pull(controller) {
              pulled = true;
              controller.enqueue(new TextEncoder().encode("cost-data"));
              controller.close();
            }
          }),
          {
            headers: {
              "Content-Disposition": 'attachment; filename="cost.csv"',
              "Content-Type": "text/csv"
            }
          }
        );
      }
    );
    vi.stubGlobal("fetch", fetchMock);

    const response = await executeAllowedFileOperation(
      env,
      "analyticsCostExport",
      "server-held-token",
      "correlation-1",
      { format: "csv", scope: "operating" }
    );

    const [input, init] = fetchMock.mock.calls[0]!;
    expect(String(input)).toBe(
      "http://127.0.0.1:8081/internal/v1/costs/export"
      + "?format=csv&scope=operating"
    );
    expect(init?.headers).toEqual({
      Accept: "*/*",
      Authorization: "Bearer server-held-token",
      "X-Correlation-Id": "correlation-1"
    });
    expect(await response.text()).toBe("cost-data");
    expect(pulled).toBe(true);
  });

  it("rejects an export declared above the ten-megabyte cap", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response("{}", {
          headers: { "Content-Length": String(10 * 1024 * 1024 + 1) }
        })
      )
    );

    await expect(
      boundedUpstreamStreamFetch(
        "http://127.0.0.1:8081/internal/v1/costs/export",
        { method: "GET" }
      )
    ).rejects.toThrow("export byte limit");
  });

  it.each([
    [{ "bad-key!": "value" }, "parameter name"],
    [{ include_deleted: true }, "not allowlisted"],
    [
      { farm_code: Array.from({ length: 33 }, (_, index) => `FARM-${index}`) },
      "Too many"
    ],
    [{ farm_code: "x".repeat(257) }, "too long"]
  ])("rejects invalid query input before fetch", async (query, message) => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    await expect(
      executeAllowedOperation(
        env,
        "analyticsFarms",
        "server-held-token",
        "correlation-1",
        query
      )
    ).rejects.toThrow(message);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
