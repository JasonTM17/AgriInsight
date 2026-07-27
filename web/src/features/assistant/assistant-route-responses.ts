import "server-only";

import { randomUUID } from "node:crypto";

import { NextResponse } from "next/server";
import { ZodError } from "zod";

import { AuthError } from "@/server/auth/auth-error";
import { authErrorResponse } from "@/server/auth/auth-http";
import { WorkApiError, workProblemResponse } from "@/features/work/work-api-security";

import { assistantAnswerSchema } from "./assistant-contract";
import { AssistantApiError } from "./assistant-api-security";

export function assistantRouteErrorResponse(
  error: unknown,
  correlationId?: string
): NextResponse {
  if (error instanceof AuthError) return authErrorResponse(error);
  if (error instanceof WorkApiError) {
    return workProblemResponse(error, correlationId);
  }
  if (error instanceof ZodError) {
    return workProblemResponse(
      new AssistantApiError(
        "validation_failed",
        400,
        error.issues[0]?.message ?? "Câu hỏi chưa hợp lệ."
      ),
      correlationId
    );
  }
  return workProblemResponse(
    new AssistantApiError(
      "assistant_unavailable",
      502,
      "Trợ lý dữ liệu tạm thời chưa sẵn sàng."
    ),
    correlationId ?? randomUUID()
  );
}

export async function assistantUpstreamResponse(
  upstream: Response,
  correlationId: string
): Promise<NextResponse> {
  if (!upstream.ok) {
    return workProblemResponse(
      problemForUpstreamStatus(upstream.status),
      correlationId
    );
  }
  try {
    const answer = assistantAnswerSchema.parse(await upstream.json());
    return NextResponse.json(answer, {
      headers: {
        "Cache-Control": "no-store",
        "X-Correlation-Id": correlationId
      }
    });
  } catch {
    return workProblemResponse(
      new AssistantApiError(
        "invalid_upstream_response",
        502,
        "Trợ lý trả về phản hồi không hợp lệ."
      ),
      correlationId
    );
  }
}

function problemForUpstreamStatus(status: number): AssistantApiError {
  if (status === 401) {
    return new AssistantApiError(
      "session_expired",
      401,
      "Phiên làm việc đã hết hạn."
    );
  }
  if (status === 403) {
    return new AssistantApiError(
      "scope_denied",
      403,
      "Câu hỏi không nằm trong phạm vi dữ liệu được cấp quyền."
    );
  }
  if (status === 400 || status === 422) {
    return new AssistantApiError(
      "query_rejected",
      400,
      "Câu hỏi hoặc lịch sử hội thoại chưa hợp lệ."
    );
  }
  if (status === 429) {
    return new AssistantApiError(
      "assistant_rate_limited",
      429,
      "Đã đạt giới hạn sử dụng trợ lý. Hãy chờ rồi thử lại."
    );
  }
  return new AssistantApiError(
    "assistant_unavailable",
    status === 503 ? 503 : 502,
    "Trợ lý dữ liệu tạm thời chưa sẵn sàng."
  );
}
