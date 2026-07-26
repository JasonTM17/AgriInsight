import "server-only";

import { randomUUID } from "node:crypto";

import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { z } from "zod";

import {
  CSRF_COOKIE_NAME,
  SESSION_COOKIE_NAME
} from "@/server/auth/cookie-policy";
import { assertCsrf } from "@/server/auth/csrf";
import { assertSameOriginMutation } from "@/server/auth/origin-guard";
import { getAuthRuntime } from "@/server/auth/runtime";
import { assertTrustedRequest } from "@/server/config/environment";

const MAX_REQUEST_BYTES = 64 * 1024;
const idempotencyKeySchema = z.string().regex(/^[\x21-\x7e]{1,200}$/);

export type WorkMutationContext = Readonly<{
  accessToken: string;
  correlationId: string;
  env: ReturnType<typeof getAuthRuntime>["env"];
  idempotencyKey: string;
}>;

export class WorkApiError extends Error {
  constructor(
    readonly code: string,
    readonly status: number,
    message: string
  ) {
    super(message);
    this.name = "WorkApiError";
  }
}

export async function authorizeWorkMutation(
  request: NextRequest
): Promise<WorkMutationContext> {
  const runtime = getAuthRuntime();
  assertTrustedRequest(request, runtime.env);
  assertSameOriginMutation(request, runtime.env);
  const sessionToken = request.cookies.get(SESSION_COOKIE_NAME)?.value;
  assertCsrf(
    request,
    request.cookies.get(CSRF_COOKIE_NAME)?.value,
    sessionToken,
    runtime.env.csrfKey
  );
  const session = await runtime.auth.requireSession(sessionToken);
  const idempotencyKey = idempotencyKeySchema.safeParse(
    request.headers.get("Idempotency-Key")
  );
  if (!idempotencyKey.success) {
    throw new WorkApiError(
      "invalid_idempotency_key",
      400,
      "Yêu cầu thiếu khóa chống trùng hợp lệ."
    );
  }
  return {
    accessToken: session.accessToken,
    correlationId: randomUUID(),
    env: runtime.env,
    idempotencyKey: idempotencyKey.data
  };
}

export async function readBoundedJson(request: NextRequest): Promise<unknown> {
  const contentType = request.headers.get("Content-Type")?.split(";", 1)[0];
  if (contentType?.trim().toLowerCase() !== "application/json") {
    throw new WorkApiError(
      "invalid_content_type",
      415,
      "Yêu cầu phải dùng định dạng JSON."
    );
  }
  const declaredLength = Number(request.headers.get("Content-Length"));
  if (Number.isFinite(declaredLength) && declaredLength > MAX_REQUEST_BYTES) {
    throw new WorkApiError(
      "request_too_large",
      413,
      "Nội dung yêu cầu vượt quá giới hạn."
    );
  }
  const body = await readBoundedText(request);
  try {
    return JSON.parse(body);
  } catch {
    throw new WorkApiError(
      "invalid_json",
      400,
      "Nội dung JSON không hợp lệ."
    );
  }
}

async function readBoundedText(request: NextRequest): Promise<string> {
  if (!request.body) return "";
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      totalBytes += value.byteLength;
      if (totalBytes > MAX_REQUEST_BYTES) {
        await reader.cancel();
        throw new WorkApiError(
          "request_too_large",
          413,
          "Nội dung yêu cầu vượt quá giới hạn."
        );
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    throw new WorkApiError(
      "invalid_json",
      400,
      "Nội dung JSON không hợp lệ."
    );
  }
}

export function workProblemResponse(
  error: WorkApiError,
  correlationId: string = randomUUID()
): NextResponse {
  return NextResponse.json(
    {
      type: "about:blank",
      title: error.message,
      status: error.status,
      code: error.code,
      correlationId
    },
    {
      status: error.status,
      headers: {
        "Cache-Control": "no-store",
        "Content-Type": "application/problem+json",
        "X-Correlation-Id": correlationId
      }
    }
  );
}
