import "server-only";

import { randomUUID } from "node:crypto";

import { NextResponse } from "next/server";
import { ZodError } from "zod";

import { AuthError } from "@/server/auth/auth-error";
import { authErrorResponse } from "@/server/auth/auth-http";
import { WorkApiError, workProblemResponse } from "@/features/work/work-api-security";

export class CostApiError extends WorkApiError {
  constructor(code: string, status: number, message: string) {
    super(code, status, message);
    this.name = "CostApiError";
  }
}

export function costRouteErrorResponse(
  error: unknown,
  correlationId?: string
): NextResponse {
  if (error instanceof AuthError) return authErrorResponse(error);
  if (error instanceof WorkApiError) {
    return workProblemResponse(error, correlationId);
  }
  if (error instanceof ZodError) {
    return workProblemResponse(
      new CostApiError(
        "validation_failed",
        400,
        error.issues[0]?.message ?? "Dữ liệu chi phí chưa hợp lệ."
      ),
      correlationId
    );
  }
  return workProblemResponse(
    new CostApiError(
      "upstream_unavailable",
      502,
      "Máy chủ vận hành tạm thời chưa xử lý được yêu cầu chi phí."
    ),
    correlationId ?? randomUUID()
  );
}

export function costMutationResponse(
  upstream: Response,
  correlationId: string
): Promise<NextResponse> {
  if (!upstream.ok) {
    const status = [400, 401, 403, 409, 422].includes(upstream.status)
      ? upstream.status
      : 502;
    const messageByStatus: Readonly<Record<number, string>> = {
      400: "Bản ghi chi phí chưa đáp ứng quy tắc nghiệp vụ.",
      401: "Phiên làm việc đã hết hạn.",
      403: "Bạn không còn quyền ghi trong phạm vi chi phí này.",
      409: "Máy chủ đã có trạng thái chi phí khác. Hãy tải lại trước khi gửi.",
      422: "Bản ghi chi phí chưa đáp ứng quy tắc nghiệp vụ."
    };
    return Promise.resolve(
      workProblemResponse(
        new CostApiError(
          status === 409 ? "cost_conflict" : status === 401 ? "session_expired" : "cost_write_rejected",
          status,
          messageByStatus[status] ?? "Máy chủ chi phí tạm thời chưa sẵn sàng."
        ),
        correlationId
      )
    );
  }
  return parseMutationJson(upstream, correlationId);
}

async function parseMutationJson(
  upstream: Response,
  correlationId: string
): Promise<NextResponse> {
  try {
    const body = await upstream.json();
    return NextResponse.json(body, {
      status: upstream.status,
      headers: {
        "Cache-Control": "no-store",
        "X-Correlation-Id": correlationId
      }
    });
  } catch {
    return workProblemResponse(
      new CostApiError(
        "invalid_upstream_response",
        502,
        "Máy chủ vận hành trả về phản hồi chi phí không hợp lệ."
      ),
      correlationId
    );
  }
}
