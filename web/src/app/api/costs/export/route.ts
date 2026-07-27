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
import { executeAllowedOperation } from "@/server/bff/upstream-client";

export async function GET(request: NextRequest) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeCostRead(request);
    correlationId = context.correlationId;
    const query = readCostExportQuery(request);
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
    return forwardCostExport(upstream, context.correlationId);
  } catch (error) {
    return costRouteErrorResponse(error, correlationId);
  }
}
