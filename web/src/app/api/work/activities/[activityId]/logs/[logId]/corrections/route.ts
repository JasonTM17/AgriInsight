import type { NextRequest } from "next/server";
import { ZodError } from "zod";

import { correctWorkLog } from "@/features/work/correct-work-log";
import {
  authorizeWorkMutation,
  readBoundedJson,
  WorkApiError,
  workProblemResponse
} from "@/features/work/work-api-security";
import { toWorkMutationResponse } from "@/features/work/work-mutation-response";
import { AuthError } from "@/server/auth/auth-error";
import { authErrorResponse } from "@/server/auth/auth-http";

export async function POST(
  request: NextRequest,
  {
    params
  }: { params: Promise<{ activityId: string; logId: string }> }
) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeWorkMutation(request);
    correlationId = context.correlationId;
    const body = await readBoundedJson(request);
    const { activityId, logId } = await params;
    const input = isRecord(body)
      ? { ...body, activityId, logId }
      : { activityId, logId };
    const upstream = await correctWorkLog(context, input);
    return toWorkMutationResponse(upstream, context.correlationId);
  } catch (error) {
    return mutationErrorResponse(error, correlationId);
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function mutationErrorResponse(
  error: unknown,
  correlationId?: string
) {
  if (error instanceof AuthError) return authErrorResponse(error);
  if (error instanceof WorkApiError) {
    return workProblemResponse(error, correlationId);
  }
  if (error instanceof ZodError) {
    return workProblemResponse(
      new WorkApiError(
        "validation_failed",
        400,
        error.issues[0]?.message ?? "Bản hiệu chỉnh chưa hợp lệ."
      ),
      correlationId
    );
  }
  return workProblemResponse(
    new WorkApiError(
      "upstream_unavailable",
      502,
      "Máy chủ vận hành tạm thời chưa nhận được bản hiệu chỉnh."
    ),
    correlationId
  );
}
