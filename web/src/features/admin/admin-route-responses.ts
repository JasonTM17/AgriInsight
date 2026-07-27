import "server-only";

import { randomUUID } from "node:crypto";

import { NextResponse } from "next/server";
import { ZodError } from "zod";

import {
  WorkApiError,
  workProblemResponse
} from "@/features/work/work-api-security";
import { AuthError } from "@/server/auth/auth-error";
import { authErrorResponse } from "@/server/auth/auth-http";

export class AdminApiError extends WorkApiError {
  constructor(code: string, status: number, message: string) {
    super(code, status, message);
    this.name = "AdminApiError";
  }
}

const STATUS_ERRORS: Readonly<
  Record<number, Readonly<{ code: string; message: string }>>
> = {
  400: {
    code: "validation_failed",
    message: "Thay đổi quản trị chưa đáp ứng contract nghiệp vụ."
  },
  401: { code: "session_expired", message: "Phiên làm việc đã hết hạn." },
  403: {
    code: "scope_denied",
    message: "Phạm vi quản trị hiện tại không cho phép thay đổi này."
  },
  404: {
    code: "admin_resource_not_found",
    message: "Tài nguyên quản trị không còn tồn tại trong tenant."
  },
  409: {
    code: "admin_conflict",
    message: "Trạng thái đã thay đổi. Hãy tải lại trước khi gửi lại."
  }
};

export function readAdminIfMatch(
  value: string | null,
  required: boolean
): string | undefined {
  if (!required) {
    if (value !== null) {
      throw new AdminApiError(
        "unexpected_if_match",
        400,
        "Thao tác này không chấp nhận phiên bản tài nguyên."
      );
    }
    return undefined;
  }
  if (!value || !/^"\d{1,19}"$/.test(value)) {
    throw new AdminApiError(
      "invalid_if_match",
      400,
      "Yêu cầu thiếu phiên bản tài nguyên hợp lệ."
    );
  }
  return value;
}

export function toAdminMutationResponse(
  upstream: Response,
  correlationId: string
): NextResponse {
  if (!upstream.ok) {
    const mapped = STATUS_ERRORS[upstream.status] ?? {
      code: "upstream_unavailable",
      message: "Máy chủ tạm thời chưa xử lý được thay đổi quản trị."
    };
    return workProblemResponse(
      new AdminApiError(
        mapped.code,
        STATUS_ERRORS[upstream.status] ? upstream.status : 502,
        mapped.message
      ),
      correlationId
    );
  }
  return NextResponse.json(
    { correlationId, status: "accepted" },
    {
      status: 200,
      headers: {
        "Cache-Control": "no-store",
        "X-Correlation-Id": correlationId
      }
    }
  );
}

export function adminRouteErrorResponse(
  error: unknown,
  correlationId: string = randomUUID()
): NextResponse {
  if (error instanceof AuthError) return authErrorResponse(error);
  if (error instanceof WorkApiError) {
    return workProblemResponse(error, correlationId);
  }
  if (error instanceof ZodError) {
    return workProblemResponse(
      new AdminApiError(
        "validation_failed",
        400,
        error.issues[0]?.message ?? "Thay đổi quản trị chưa hợp lệ."
      ),
      correlationId
    );
  }
  return workProblemResponse(
    new AdminApiError(
      "upstream_unavailable",
      502,
      "Máy chủ tạm thời chưa xử lý được thay đổi quản trị."
    ),
    correlationId
  );
}
