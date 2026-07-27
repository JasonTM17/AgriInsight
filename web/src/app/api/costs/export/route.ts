import type { NextRequest } from "next/server";

import { authorizeCostRead } from "@/features/costs/cost-api-security";
import {
  CostApiError,
  costRouteErrorResponse
} from "@/features/costs/cost-route-responses";
import {
  forwardCostExport,
  readCostExportQuery
} from "@/features/costs/export-cost-view";
import { executeAllowedFileOperation } from "@/server/bff/upstream-client";

export async function GET(request: NextRequest) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeCostRead(request);
    correlationId = context.correlationId;
    const query = readCostExportQuery(request);
    const upstream = await executeAllowedFileOperation(
      context.env,
      "analyticsCostExport",
      context.accessToken,
      context.correlationId,
      query
    );
    if (!upstream.ok) {
      throw exportUpstreamError(upstream.status);
    }
    return forwardCostExport(upstream, context.correlationId);
  } catch (error) {
    return costRouteErrorResponse(error, correlationId);
  }
}

function exportUpstreamError(status: number): CostApiError {
  if (status === 401) {
    return new CostApiError(
      "session_expired",
      401,
      "Phiên làm việc đã hết hạn."
    );
  }
  if (status === 403) {
    return new CostApiError(
      "scope_denied",
      403,
      "Bản xuất chi phí không nằm trong phạm vi được cấp quyền."
    );
  }
  if (status === 400 || status === 422) {
    return new CostApiError(
      "export_rejected",
      status,
      "Bản xuất vượt giới hạn hoặc bộ lọc chưa hợp lệ. Hãy thu hẹp phạm vi."
    );
  }
  if (status === 503) {
    return new CostApiError(
      "export_format_unavailable",
      503,
      "Định dạng xuất này tạm thời chưa sẵn sàng."
    );
  }
  return new CostApiError(
    "export_unavailable",
    502,
    "Bản xuất chi phí tạm thời chưa sẵn sàng."
  );
}
