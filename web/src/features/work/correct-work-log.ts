import "server-only";

import { executeAllowedMutation } from "@/server/bff/upstream-client";

import {
  correctWorkLogSchema
} from "./work-mutation-contract";
import type { WorkMutationContext } from "./work-api-security";

export async function correctWorkLog(
  context: WorkMutationContext,
  input: unknown
): Promise<Response> {
  const parsed = correctWorkLogSchema.parse(input);
  const { activityId, logId, ...payload } = parsed;
  return executeAllowedMutation(
    context.env,
    "activityLogCorrection",
    context.accessToken,
    context.correlationId,
    context.idempotencyKey,
    { ...payload, reasonCode: "FIELD_LOG_CORRECTION" },
    { id: activityId, logId }
  );
}
