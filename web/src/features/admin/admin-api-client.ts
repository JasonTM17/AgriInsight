"use client";

import type { AdminMutationCommand } from "./admin-mutation-contract";

const CSRF_COOKIE_NAME = "__Host-agriinsight-csrf";

export type AdminApiProblem = Readonly<{
  code?: string;
  correlationId?: string;
  title: string;
}>;

export type AdminMutationResult =
  | Readonly<{ ok: true }>
  | Readonly<{
      ambiguous: boolean;
      ok: false;
      problem: AdminApiProblem;
    }>;

export async function postAdminMutation(
  command: AdminMutationCommand,
  idempotencyKey: string,
  ifMatch?: string
): Promise<AdminMutationResult> {
  const csrfToken = readCookie(CSRF_COOKIE_NAME);
  if (!csrfToken) {
    return {
      ambiguous: false,
      ok: false,
      problem: { title: "Phiên bảo mật không còn hợp lệ. Hãy đăng nhập lại." }
    };
  }
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "Idempotency-Key": idempotencyKey,
    "X-AgriInsight-Csrf": csrfToken
  };
  if (ifMatch) headers["If-Match"] = ifMatch;
  const response = await fetch("/api/administration/mutations", {
    method: "POST",
    body: JSON.stringify(command),
    credentials: "same-origin",
    headers
  });
  if (response.ok) return { ok: true };
  return {
    ambiguous: response.status >= 500,
    ok: false,
    problem: await readProblem(response)
  };
}

async function readProblem(response: Response): Promise<AdminApiProblem> {
  try {
    const body = await response.json() as Record<string, unknown>;
    return {
      code: typeof body.code === "string" ? body.code : undefined,
      correlationId: typeof body.correlationId === "string"
        ? body.correlationId
        : response.headers.get("X-Correlation-Id") ?? undefined,
      title: typeof body.title === "string"
        ? body.title
        : "Máy chủ chưa chấp nhận thay đổi quản trị."
    };
  } catch {
    return {
      correlationId: response.headers.get("X-Correlation-Id") ?? undefined,
      title: "Máy chủ chưa chấp nhận thay đổi quản trị."
    };
  }
}

function readCookie(name: string): string | undefined {
  const prefix = `${encodeURIComponent(name)}=`;
  const entry = document.cookie
    .split("; ")
    .find((cookie) => cookie.startsWith(prefix));
  return entry ? decodeURIComponent(entry.slice(prefix.length)) : undefined;
}
