import type { NextRequest } from "next/server";

import { authorizeCostRead } from "@/features/costs/cost-api-security";
import {
  CostApiError,
  costRouteErrorResponse
} from "@/features/costs/cost-route-responses";
import { executeAllowedOperation } from "@/server/bff/upstream-client";

const VALUE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/;
const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const FORMATS = new Set(["csv", "pdf", "xlsx"]);
const SCOPES = new Set(["all", "operating", "procurement"]);

export async function GET(request: NextRequest) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeCostRead(request);
    correlationId = context.correlationId;
    const query = readExportQuery(request);
    const upstream = await executeAllowedOperation(
      context.env,
      "analyticsCostExport",
      context.accessToken,
      context.correlationId,
      query
    );
    if (!upstream.ok) {
      throw new CostApiError(
        upstream.status === 403 ? "scope_denied" : "export_unavailable",
        upstream.status === 403 ? 403 : 502,
        upstream.status === 403
          ? "Bản xuất chi phí không nằm trong phạm vi được cấp quyền."
          : "Bản xuất chi phí tạm thời chưa sẵn sàng."
      );
    }
    return forwardExport(upstream, context.correlationId);
  } catch (error) {
    return costRouteErrorResponse(error, correlationId);
  }
}

function readExportQuery(
  request: NextRequest
): Readonly<Record<string, string | number>> {
  const params = request.nextUrl.searchParams;
  const format = params.get("format") ?? "csv";
  const scope = params.get("scope") ?? "all";
  const farm = optionalValue(params.get("farm"), "farm");
  const crop = optionalValue(params.get("crop"), "crop");
  const activity = optionalValue(params.get("activity"), "activity");
  const supplier = optionalValue(params.get("supplier"), "supplier");
  const season = optionalValue(params.get("season"), "season");
  const monthFrom = optionalMonth(params.get("month_from"), "month_from");
  const monthTo = optionalMonth(params.get("month_to"), "month_to");
  const rawTopN = params.get("top_n") ?? "15";
  if (!FORMATS.has(format)) invalid("format", "Định dạng xuất không được hỗ trợ.");
  if (!SCOPES.has(scope)) invalid("scope", "Phạm vi xuất không được hỗ trợ.");
  const topN = Number(rawTopN);
  if (!Number.isInteger(topN) || topN < 1 || topN > 30) {
    invalid("top_n", "top_n phải nằm trong khoảng từ 1 đến 30.");
  }
  if (monthFrom && monthTo && monthFrom > monthTo) {
    invalid("month_range", "month_from không được sau month_to.");
  }
  const query: Record<string, string | number> = {
    format,
    scope,
    top_n: topN
  };
  for (const [key, value] of Object.entries({
    farm,
    crop,
    activity,
    supplier,
    season,
    month_from: monthFrom,
    month_to: monthTo
  })) {
    if (value !== undefined) query[key] = value;
  }
  return query;
}

function optionalValue(value: string | null, name: string): string | undefined {
  if (value === null || value.trim() === "") return undefined;
  const normalized = value.trim();
  if (!VALUE_PATTERN.test(normalized)) invalid(name, `${name} không hợp lệ.`);
  return normalized;
}

function optionalMonth(value: string | null, name: string): string | undefined {
  if (value === null || value.trim() === "") return undefined;
  const normalized = value.trim();
  if (!MONTH_PATTERN.test(normalized)) invalid(name, `${name} phải dùng YYYY-MM.`);
  return normalized;
}

function invalid(code: string, message: string): never {
  throw new CostApiError("validation_failed", 400, `${code}: ${message}`);
}

function forwardExport(upstream: Response, correlationId: string): Response {
  const headers = new Headers({
    "Cache-Control": "no-store",
    "X-Correlation-Id": correlationId
  });
  for (const name of [
    "Content-Disposition",
    "Content-Length",
    "Content-Type",
    "X-AgriInsight-Export-Metadata"
  ]) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  const contentType = headers.get("Content-Type");
  if (
    !contentType
    || !/^(?:text\/csv|application\/pdf|application\/vnd\.openxmlformats-officedocument\.spreadsheetml\.sheet)(?:;|$)/.test(contentType)
  ) {
    throw new CostApiError(
      "invalid_upstream_response",
      502,
      "Máy chủ xuất trả về định dạng không hợp lệ."
    );
  }
  return new Response(upstream.body, {
    status: upstream.status,
    headers
  });
}
