import "server-only";

import { WorkApiError, workProblemResponse } from "@/features/work/work-api-security";
import { NextResponse } from "next/server";
import { ZodError } from "zod";

import { inventoryTransactionSchema } from "./inventory-generated-contract-schemas";

const STATUS_ERRORS: Readonly<
  Record<number, Readonly<{ code: string; message: string }>>
> = {
  400: {
    code: "validation_failed",
    message: "Giao dịch kho chưa đáp ứng quy tắc nghiệp vụ."
  },
  401: {
    code: "session_expired",
    message: "Phiên làm việc đã hết hạn."
  },
  403: {
    code: "scope_denied",
    message: "Bạn không còn quyền ghi trong phạm vi kho này."
  },
  404: {
    code: "inventory_not_found",
    message: "Giao dịch hoặc kho không còn trong phạm vi."
  },
  409: {
    code: "inventory_conflict",
    message: "Máy chủ đã có trạng thái khác. Hãy tải lại trước khi gửi."
  }
};

export async function toInventoryMutationResponse(
  upstream: Response,
  correlationId: string
): Promise<NextResponse> {
  if (!upstream.ok) {
    const mapped = STATUS_ERRORS[upstream.status] ?? {
      code: "upstream_unavailable",
      message: "Máy chủ vận hành tạm thời chưa nhận được giao dịch kho."
    };
    const status = STATUS_ERRORS[upstream.status] ? upstream.status : 502;
    return workProblemResponse(
      new WorkApiError(mapped.code, status, mapped.message),
      correlationId
    );
  }
  try {
    const transaction = inventoryTransactionSchema.parse(await upstream.json());
    const etag = upstream.headers.get("ETag");
    if (!etag || !/^"\d{1,19}"$/.test(etag)) {
      return workProblemResponse(
        new WorkApiError(
          "invalid_upstream_response",
          502,
          "Máy chủ vận hành trả về phiên bản giao dịch không hợp lệ."
        ),
        correlationId
      );
    }
    return NextResponse.json(transaction, {
      status: upstream.status,
      headers: {
        "Cache-Control": "no-store",
        ETag: etag,
        "X-Correlation-Id": correlationId
      }
    });
  } catch (error) {
    if (error instanceof ZodError) {
      return workProblemResponse(
        new WorkApiError(
          "invalid_upstream_response",
          502,
          "Máy chủ vận hành trả về giao dịch kho không hợp lệ."
        ),
        correlationId
      );
    }
    throw error;
  }
}
