"use client";

import type { ZodType } from "zod";

import {
  realtimeAlertAcknowledgementBodySchema,
  realtimeAlertParamsSchema,
  realtimeOperationalAlertFeedSchema,
  realtimeOperationalAlertSchema,
  type RealtimeOperationalAlert,
  type RealtimeOperationalAlertFeed
} from "./realtime-alert-contract";

const CSRF_COOKIE_NAME = "__Host-agriinsight-csrf";
const ALERTS_PATH = "/api/realtime/alerts";

export type RealtimeAlertClientProblemCode =
  | "alert_conflict"
  | "alert_not_found"
  | "invalid_alert_id"
  | "invalid_idempotency_key"
  | "invalid_request"
  | "invalid_response"
  | "invalid_session"
  | "missing_csrf"
  | "realtime_alert_unavailable"
  | "scope_denied";

export type RealtimeAlertClientProblem = Readonly<{
  code: RealtimeAlertClientProblemCode;
  correlationId?: string;
  status: number;
  title: string;
}>;

export type RealtimeAlertClientResult<Value> =
  | Readonly<{ data: Value; ok: true }>
  | Readonly<{
      ambiguous: boolean;
      ok: false;
      problem: RealtimeAlertClientProblem;
    }>;

export async function getRealtimeOperationalAlerts(
  signal?: AbortSignal
): Promise<RealtimeAlertClientResult<RealtimeOperationalAlertFeed>> {
  try {
    const response = await fetch(ALERTS_PATH, {
      cache: "no-store",
      credentials: "same-origin",
      signal
    });
    return parseAlertResponse(response, realtimeOperationalAlertFeedSchema, false);
  } catch (cause) {
    if (isAbortError(cause)) throw cause;
    return failure(unavailableProblem(), false);
  }
}

export async function acknowledgeRealtimeOperationalAlert(
  alertId: string,
  idempotencyKey: string,
  signal?: AbortSignal
): Promise<RealtimeAlertClientResult<RealtimeOperationalAlert>> {
  const params = realtimeAlertParamsSchema.safeParse({ alertId });
  if (!params.success) {
    return failure({
      code: "invalid_alert_id",
      status: 400,
      title: "Cảnh báo vận hành không hợp lệ."
    }, false);
  }
  if (!/^[\x21-\x7e]{1,200}$/.test(idempotencyKey)) {
    return failure({
      code: "invalid_idempotency_key",
      status: 400,
      title: "Yêu cầu thiếu khóa chống trùng hợp lệ."
    }, false);
  }
  const csrfToken = readCsrfCookie();
  if (!csrfToken) {
    return failure({
      code: "missing_csrf",
      status: 403,
      title: "Phiên bảo mật không còn hợp lệ. Hãy đăng nhập lại."
    }, false);
  }

  const body = realtimeAlertAcknowledgementBodySchema.parse({});
  try {
    const response = await fetch(
      `${ALERTS_PATH}/${encodeURIComponent(params.data.alertId)}/acknowledgements`,
      {
        body: JSON.stringify(body),
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey,
          "X-AgriInsight-Csrf": csrfToken
        },
        method: "POST",
        signal
      }
    );
    return parseAlertResponse(response, realtimeOperationalAlertSchema, true);
  } catch (cause) {
    if (isAbortError(cause)) throw cause;
    return failure(unavailableProblem(), true);
  }
}

async function parseAlertResponse<Value>(
  response: Response,
  schema: ZodType<Value>,
  ambiguousOnInvalidResponse: boolean
): Promise<RealtimeAlertClientResult<Value>> {
  if (!response.ok) {
    return failure(
      await readSafeProblem(response),
      ambiguousOnInvalidResponse && response.status >= 500
    );
  }
  if (response.status !== 200) {
    return failure(invalidResponseProblem(response), ambiguousOnInvalidResponse);
  }
  try {
    return { data: schema.parse(await response.json()), ok: true };
  } catch {
    return failure(invalidResponseProblem(response), ambiguousOnInvalidResponse);
  }
}

async function readSafeProblem(response: Response): Promise<RealtimeAlertClientProblem> {
  const headerCorrelationId = safeCorrelationId(
    response.headers.get("X-Correlation-Id")
  );
  let bodyCorrelationId: string | undefined;
  try {
    const body: unknown = await response.json();
    if (isRecord(body)) bodyCorrelationId = safeCorrelationId(body.correlationId);
  } catch {
    // The response body is never surfaced to the caller.
  }
  const safeProblem = SAFE_PROBLEMS[response.status] ?? unavailableProblem();
  return {
    ...safeProblem,
    correlationId: headerCorrelationId ?? bodyCorrelationId
  };
}

function invalidResponseProblem(response: Response): RealtimeAlertClientProblem {
  return {
    code: "invalid_response",
    correlationId: safeCorrelationId(response.headers.get("X-Correlation-Id")),
    status: 502,
    title: "Máy chủ cảnh báo vận hành trả về dữ liệu không hợp lệ."
  };
}

function unavailableProblem(): RealtimeAlertClientProblem {
  return {
    code: "realtime_alert_unavailable",
    status: 502,
    title: "Máy chủ cảnh báo vận hành tạm thời không khả dụng."
  };
}

function failure<Value>(
  problem: RealtimeAlertClientProblem,
  ambiguous: boolean
): RealtimeAlertClientResult<Value> {
  return { ambiguous, ok: false, problem };
}

function readCsrfCookie(): string | undefined {
  if (typeof document === "undefined") return undefined;
  const prefix = `${encodeURIComponent(CSRF_COOKIE_NAME)}=`;
  const entry = document.cookie.split("; ").find((cookie) => cookie.startsWith(prefix));
  if (!entry) return undefined;
  try {
    const token = decodeURIComponent(entry.slice(prefix.length));
    return token || undefined;
  } catch {
    return undefined;
  }
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === "AbortError";
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === "object" && value !== null;
}

function safeCorrelationId(value: unknown): string | undefined {
  return typeof value === "string"
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
    ? value
    : undefined;
}

const SAFE_PROBLEMS: Readonly<Record<number, RealtimeAlertClientProblem>> = {
  400: {
    code: "invalid_request",
    status: 400,
    title: "Yêu cầu cảnh báo chưa hợp lệ."
  },
  401: {
    code: "invalid_session",
    status: 401,
    title: "Phiên đăng nhập đã hết hạn hoặc không còn hợp lệ."
  },
  403: {
    code: "scope_denied",
    status: 403,
    title: "Phiên hiện tại không còn quyền truy cập cảnh báo vận hành."
  },
  404: {
    code: "alert_not_found",
    status: 404,
    title: "Cảnh báo không còn mở hoặc không còn trong phạm vi."
  },
  409: {
    code: "alert_conflict",
    status: 409,
    title: "Yêu cầu trùng với một thao tác cảnh báo khác."
  }
};
