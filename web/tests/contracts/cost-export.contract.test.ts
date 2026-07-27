import { describe, expect, it } from "vitest";

import { NextRequest } from "next/server";

import { forwardCostExport, readCostExportQuery } from "@/features/costs/export-cost-view";
import { CostApiError } from "@/features/costs/cost-route-responses";

describe("cost export BFF contract", () => {
  it("normalizes only allowlisted filters", () => {
    const request = new NextRequest(
      "http://localhost/api/costs/export?format=csv&scope=operating&farm=FARM-001&month_from=2026-01&month_to=2026-07&top_n=7"
    );
    expect(readCostExportQuery(request)).toEqual({
      format: "csv",
      scope: "operating",
      farm: "FARM-001",
      month_from: "2026-01",
      month_to: "2026-07",
      top_n: 7
    });
  });

  it("rejects unsupported format and path-like values before upstream", () => {
    expect(() =>
      readCostExportQuery(
        new NextRequest("http://localhost/api/costs/export?format=zip")
      )
    ).toThrowError(CostApiError);
    expect(() =>
      readCostExportQuery(
        new NextRequest("http://localhost/api/costs/export?farm=../secret")
      )
    ).toThrowError(CostApiError);
  });

  it("forwards only safe file headers and preserves the stream", async () => {
    const upstream = new Response("csv-content", {
      headers: {
        "Content-Disposition": 'attachment; filename="cost.csv"',
        "Content-Length": "11",
        "Content-Type": "text/csv; charset=utf-8",
        "X-AgriInsight-Export-Metadata": "{\"format\":\"csv\"}",
        "X-Internal-Path": "must-not-forward"
      }
    });
    const response = forwardCostExport(upstream, "corr-cost-001");
    expect(response.headers.get("X-Correlation-Id")).toBe("corr-cost-001");
    expect(response.headers.get("X-Internal-Path")).toBeNull();
    expect(await response.text()).toBe("csv-content");
  });
});
