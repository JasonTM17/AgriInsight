import "server-only";

import { randomUUID } from "node:crypto";

import { NextResponse } from "next/server";
import { ZodError, type ZodType } from "zod";

import {
  RealtimeAlertApiError
} from "@/features/realtime-alerts/realtime-alert-api-security";
import {
  realtimeOperationalAlertFeedSchema,
  realtimeOperationalAlertSchema
} from "@/features/realtime-alerts/realtime-alert-contract";
import { AuthError } from "@/server/auth/auth-error";
import { authErrorResponse } from "@/server/auth/auth-http";
import {
  WorkApiError,
  workProblemResponse
} from "@/features/work/work-api-security";

const STATUS_ERRORS: Readonly<
  Record<number, Readonly<{ code: string; message: string }>>
> = {
  401: {
    code: "invalid_session",
    message: "Phiên đăng nhập đã hết hạn hoặc không còn hợp lệ."
  },
  403: {
    code: "scope_denied",
    message: "Phiên hiện tại không còn quyền truy cập cảnh báo vận hành."
  },
  404: {
    code: "alert_not_found",
    message: "Cảnh báo không còn mở hoặc không còn trong phạm vi."
  },
  409: {
    code: "alert_conflict",
    message: "Yêu cầu trùng với một thao tác cảnh báo khác."
  }
};
const FEED_ERROR_STATUSES = new Set([401, 403]);
const ACKNOWLEDGEMENT_ERROR_STATUSES = new Set([401, 403, 404, 409]);

export function realtimeAlertRouteErrorResponse(
  error: unknown,
  correlationId?: string
): NextResponse {
  if (error instanceof AuthError) return authErrorResponse(error);
  if (error instanceof WorkApiError) {
    return workProblemResponse(error, correlationId);
  }
  if (error instanceof ZodError) {
    return workProblemResponse(
      new RealtimeAlertApiError(
        "validation_failed",
        400,
        error.issues[0]?.message ?? "Yêu cầu cảnh báo chưa hợp lệ."
      ),
      correlationId
    );
  }
  return workProblemResponse(
    new RealtimeAlertApiError(
      "upstream_unavailable",
      502,
      "Máy chủ cảnh báo vận hành tạm thời không khả dụng."
    ),
    correlationId
  );
}

export async function realtimeAlertFeedResponse(
  upstream: Response,
  correlationId: string
): Promise<NextResponse> {
  return validatedResponse(
    upstream,
    correlationId,
    realtimeOperationalAlertFeedSchema,
    FEED_ERROR_STATUSES
  );
}

export async function realtimeAlertAcknowledgementResponse(
  upstream: Response,
  correlationId: string
): Promise<NextResponse> {
  return validatedResponse(
    upstream,
    correlationId,
    realtimeOperationalAlertSchema,
    ACKNOWLEDGEMENT_ERROR_STATUSES
  );
}

async function validatedResponse<Output>(
  upstream: Response,
  correlationId: string,
  schema: ZodType<Output>,
  safeErrorStatuses: ReadonlySet<number>
): Promise<NextResponse> {
  if (!upstream.ok) {
    const mapped = safeErrorStatuses.has(upstream.status)
      ? STATUS_ERRORS[upstream.status]
      : undefined;
    return workProblemResponse(
      new RealtimeAlertApiError(
        mapped?.code ?? "upstream_unavailable",
        mapped ? upstream.status : 502,
        mapped?.message
          ?? "Máy chủ cảnh báo vận hành tạm thời không khả dụng."
      ),
      correlationId
    );
  }
  if (upstream.status !== 200) {
    return invalidUpstreamResponse(correlationId);
  }
  try {
    const parsed = schema.parse(await upstream.json());
    return NextResponse.json(parsed, {
      status: 200,
      headers: {
        "Cache-Control": "no-store",
        "X-Correlation-Id": correlationId
      }
    });
  } catch {
    return invalidUpstreamResponse(correlationId);
  }
}

function invalidUpstreamResponse(
  correlationId: string = randomUUID(),
): NextResponse {
  return workProblemResponse(
    new RealtimeAlertApiError(
      "invalid_upstream_response",
      502,
      "Máy chủ cảnh báo vận hành trả về dữ liệu không hợp lệ."
    ),
    correlationId
  );
}
