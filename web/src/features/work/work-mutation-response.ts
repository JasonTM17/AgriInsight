import "server-only";

import { NextResponse } from "next/server";
import { ZodError } from "zod";

import { parseWorkActivityLog } from "./work-generated-client-adapter";
import { WorkApiError, workProblemResponse } from "./work-api-security";

const STATUS_ERRORS: Readonly<
  Record<number, Readonly<{ code: string; message: string }>>
> = {
  400: {
    code: "validation_failed",
    message: "Bản ghi chưa đáp ứng quy tắc nghiệp vụ."
  },
  401: {
    code: "session_expired",
    message: "Phiên làm việc đã hết hạn."
  },
  403: {
    code: "scope_denied",
    message: "Bạn không còn quyền ghi trong phạm vi này."
  },
  404: {
    code: "work_not_found",
    message: "Công việc hoặc bản ghi không còn trong phạm vi."
  },
  409: {
    code: "work_conflict",
    message: "Máy chủ đã có trạng thái khác. Hãy tải lại trước khi gửi."
  },
  422: {
    code: "validation_failed",
    message: "Bản ghi chưa đáp ứng quy tắc nghiệp vụ."
  }
};

export async function toWorkMutationResponse(
  upstream: Response,
  correlationId: string
): Promise<NextResponse> {
  if (!upstream.ok) {
    const mapped = STATUS_ERRORS[upstream.status] ?? {
      code: "upstream_unavailable",
      message: "Máy chủ vận hành tạm thời chưa nhận được bản ghi."
    };
    const status = STATUS_ERRORS[upstream.status] ? upstream.status : 502;
    return workProblemResponse(
      new WorkApiError(mapped.code, status, mapped.message),
      correlationId
    );
  }
  try {
    const log = parseWorkActivityLog(await upstream.json());
    return NextResponse.json(log, {
      status: upstream.status,
      headers: {
        "Cache-Control": "no-store",
        "X-Correlation-Id": correlationId
      }
    });
  } catch (error) {
    if (error instanceof ZodError) {
      return workProblemResponse(
        new WorkApiError(
          "invalid_upstream_response",
          502,
          "Máy chủ vận hành trả về bản ghi không hợp lệ."
        ),
        correlationId
      );
    }
    throw error;
  }
}
