import type { NextRequest } from "next/server";

import {
  authorizeCostMutation,
  readBoundedCostJson
} from "@/features/costs/cost-api-security";
import { correctCostEntry } from "@/features/costs/correct-cost-entry";
import {
  costMutationResponse,
  costRouteErrorResponse
} from "@/features/costs/cost-route-responses";

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ entryId: string }> }
) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeCostMutation(request);
    correlationId = context.correlationId;
    const { entryId } = await params;
    const input = await readBoundedCostJson(request);
    const upstream = await correctCostEntry(context, {
      ...(typeof input === "object" && input !== null ? input : {}),
      entryId
    });
    return costMutationResponse(upstream, context.correlationId, "correction");
  } catch (error) {
    return costRouteErrorResponse(error, correlationId);
  }
}
