import "server-only";

import { executeAllowedMutation } from "@/server/bff/upstream-client";

import {
  appendWorkLogSchema
} from "./work-mutation-contract";
import type { WorkMutationContext } from "./work-api-security";

export async function submitWorkLog(
  context: WorkMutationContext,
  input: unknown
): Promise<Response> {
  const parsed = appendWorkLogSchema.parse(input);
  const { activityId, ...payload } = parsed;
  return executeAllowedMutation(
    context.env,
    "activityLogAppend",
    context.accessToken,
    context.correlationId,
    context.idempotencyKey,
    { ...payload, reasonCode: "FIELD_LOG_APPEND" },
    { id: activityId }
  );
}
