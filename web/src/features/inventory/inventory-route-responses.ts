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

import { InventoryReadError } from "./inventory-generated-client-adapter";

export class InventoryApiError extends WorkApiError {
  constructor(code: string, status: number, message: string) {
    super(code, status, message);
    this.name = "InventoryApiError";
  }
}

export function inventoryProblemResponse(
  error: WorkApiError,
  correlationId: string = randomUUID()
): NextResponse {
  return workProblemResponse(error, correlationId);
}

export function inventoryRouteErrorResponse(
  error: unknown,
  correlationId?: string
): NextResponse {
  if (error instanceof AuthError) return authErrorResponse(error);
  if (error instanceof WorkApiError) {
    return inventoryProblemResponse(error, correlationId);
  }
  if (error instanceof InventoryReadError) {
    return inventoryProblemResponse(
      readErrorToApiError(error),
      correlationId
    );
  }
  if (error instanceof ZodError) {
    return inventoryProblemResponse(
      new InventoryApiError(
        "validation_failed",
        400,
        error.issues[0]?.message ?? "Giao dịch kho chưa hợp lệ."
      ),
      correlationId
    );
  }
  return inventoryProblemResponse(
    new InventoryApiError(
      "upstream_unavailable",
      502,
      "Máy chủ vận hành tạm thời chưa xử lý được yêu cầu kho."
    ),
    correlationId
  );
}

function readErrorToApiError(error: InventoryReadError): InventoryApiError {
  const messages: Readonly<Record<InventoryReadError["kind"], string>> = {
    unauthenticated: "Phiên làm việc đã hết hạn.",
    denied: "Giao dịch không còn trong phạm vi kho.",
    not_found: "Không tìm thấy giao dịch trong phạm vi kho.",
    failure: "Không thể xác minh giao dịch kho."
  };
  const codes: Readonly<Record<InventoryReadError["kind"], string>> = {
    unauthenticated: "session_expired",
    denied: "scope_denied",
    not_found: "inventory_not_found",
    failure: "upstream_unavailable"
  };
  return new InventoryApiError(
    codes[error.kind],
    error.status,
    messages[error.kind]
  );
}
