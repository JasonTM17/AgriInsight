import "server-only";

import { executeAllowedMutation } from "@/server/bff/upstream-client";

import { postCostEntrySchema } from "./cost-mutation-contract";
import type { CostMutationContext } from "./cost-api-security";

export async function postCostEntry(
  context: CostMutationContext,
  input: unknown
): Promise<Response> {
  const parsed = postCostEntrySchema.parse(input);
  return executeAllowedMutation(
    context.env,
    "operatingCostPost",
    context.accessToken,
    context.correlationId,
    context.idempotencyKey,
    { ...parsed, reasonCode: parsed.reasonCode ?? "WEB_OPERATING_COST_POST" },
    {}
  );
}
