import "server-only";

import { executeAllowedMutation } from "@/server/bff/upstream-client";

import type { CostMutationContext } from "./cost-api-security";
import { correctCostEntrySchema } from "./cost-mutation-contract";

export async function correctCostEntry(
  context: CostMutationContext,
  input: unknown
): Promise<Response> {
  const parsed = correctCostEntrySchema.parse(input);
  const { entryId, ...payload } = parsed;
  return executeAllowedMutation(
    context.env,
    "operatingCostCorrection",
    context.accessToken,
    context.correlationId,
    context.idempotencyKey,
    { ...payload, reasonCode: payload.reasonCode ?? "WEB_OPERATING_COST_CORRECTION" },
    { id: entryId }
  );
}
