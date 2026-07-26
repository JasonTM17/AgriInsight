"use client";

const CSRF_COOKIE_NAME = "__Host-agriinsight-csrf";

export type WorkApiProblem = Readonly<{
  code?: string;
  correlationId?: string;
  title: string;
}>;

export async function postWorkMutation(
  path: string,
  payload: unknown,
  idempotencyKey: string
): Promise<Readonly<{ ok: boolean; problem?: WorkApiProblem }>> {
  const csrfToken = readCookie(CSRF_COOKIE_NAME);
  if (!csrfToken) {
    return {
      ok: false,
      problem: { title: "Phiên bảo mật không còn hợp lệ. Hãy đăng nhập lại." }
    };
  }
  const response = await fetch(path, {
    method: "POST",
    body: JSON.stringify(payload),
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      "Idempotency-Key": idempotencyKey,
      "X-AgriInsight-Csrf": csrfToken
    }
  });
  if (response.ok) return { ok: true };
  return {
    ok: false,
    problem: await readProblem(response)
  };
}

async function readProblem(response: Response): Promise<WorkApiProblem> {
  try {
    const body = await response.json() as Record<string, unknown>;
    return {
      code: typeof body.code === "string" ? body.code : undefined,
      correlationId:
        typeof body.correlationId === "string"
          ? body.correlationId
          : response.headers.get("X-Correlation-Id") ?? undefined,
      title:
        typeof body.title === "string"
          ? body.title
          : "Máy chủ chưa chấp nhận bản ghi."
    };
  } catch {
    return {
      correlationId: response.headers.get("X-Correlation-Id") ?? undefined,
      title: "Máy chủ chưa chấp nhận bản ghi."
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
