"use client";

import {
  assistantAnswerSchema,
  type AssistantAnswer,
  type AssistantQuery
} from "./assistant-contract";

const CSRF_COOKIE_NAME = "__Host-agriinsight-csrf";

export class AssistantClientError extends Error {
  constructor(
    readonly code: string,
    readonly status: number,
    message: string,
    readonly correlationId?: string
  ) {
    super(message);
    this.name = "AssistantClientError";
  }
}

export async function queryAssistant(
  query: AssistantQuery,
  signal?: AbortSignal
): Promise<AssistantAnswer> {
  const csrfToken = readCookie(CSRF_COOKIE_NAME);
  if (!csrfToken) {
    throw new AssistantClientError(
      "session_expired",
      401,
      "Phiên bảo mật không còn hợp lệ. Hãy đăng nhập lại."
    );
  }
  const response = await fetch("/api/assistant/query", {
    method: "POST",
    body: JSON.stringify(query),
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      "X-AgriInsight-Csrf": csrfToken
    },
    signal
  });
  if (!response.ok) throw await readProblem(response);
  try {
    return assistantAnswerSchema.parse(await response.json());
  } catch {
    throw new AssistantClientError(
      "invalid_response",
      502,
      "Trợ lý trả về phản hồi không hợp lệ.",
      response.headers.get("X-Correlation-Id") ?? undefined
    );
  }
}

async function readProblem(response: Response): Promise<AssistantClientError> {
  try {
    const body = await response.json() as Record<string, unknown>;
    return new AssistantClientError(
      typeof body.code === "string" ? body.code : "assistant_unavailable",
      response.status,
      typeof body.title === "string"
        ? body.title
        : "Trợ lý dữ liệu tạm thời chưa sẵn sàng.",
      typeof body.correlationId === "string"
        ? body.correlationId
        : response.headers.get("X-Correlation-Id") ?? undefined
    );
  } catch {
    return new AssistantClientError(
      "assistant_unavailable",
      response.status,
      "Trợ lý dữ liệu tạm thời chưa sẵn sàng.",
      response.headers.get("X-Correlation-Id") ?? undefined
    );
  }
}

function readCookie(name: string): string | undefined {
  const prefix = `${encodeURIComponent(name)}=`;
  const entry = document.cookie
    .split("; ")
    .find((cookie) => cookie.startsWith(prefix));
  return entry ? decodeURIComponent(entry.slice(prefix.length)) : undefined;
}
