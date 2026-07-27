import type { NextRequest } from "next/server";

import {
  authorizeCostMutation,
  readBoundedCostJson
} from "@/features/costs/cost-api-security";
import { costMutationResponse, costRouteErrorResponse } from "@/features/costs/cost-route-responses";
import { postCostEntry } from "@/features/costs/post-cost-entry";

export async function POST(request: NextRequest) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeCostMutation(request);
    correlationId = context.correlationId;
    const upstream = await postCostEntry(
      context,
      await readBoundedCostJson(request)
    );
    return costMutationResponse(upstream, context.correlationId, "posting");
  } catch (error) {
    return costRouteErrorResponse(error, correlationId);
  }
}
