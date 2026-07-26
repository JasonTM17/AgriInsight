import { afterEach, describe, expect, it, vi } from "vitest";

import {
  UpstreamResponseError,
  boundedUpstreamFetch
} from "@/server/bff/bounded-upstream-fetch";
import { executeAllowedOperation } from "@/server/bff/upstream-client";
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
