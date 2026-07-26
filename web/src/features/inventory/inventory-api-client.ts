"use client";

const CSRF_COOKIE_NAME = "__Host-agriinsight-csrf";

export type InventoryApiProblem = Readonly<{
  code?: string;
  correlationId?: string;
  title: string;
}>;

export type InventoryMutationResult =
  | Readonly<{ ok: true }>
  | Readonly<{ ok: false; problem: InventoryApiProblem }>;

export async function postInventoryMutation(
  path: string,
  payload: unknown,
  idempotencyKey: string,
  ifMatch?: string
): Promise<InventoryMutationResult> {
  const csrfToken = readCookie(CSRF_COOKIE_NAME);
  if (!csrfToken) {
    return {
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
  const response = await fetch(path, {
    method: "POST",
    body: JSON.stringify(payload),
    credentials: "same-origin",
    headers
  });
  if (response.ok) return { ok: true };
  return { ok: false, problem: await readProblem(response) };
}

export async function getInventoryTransactionEtag(
  transactionId: string
): Promise<string> {
  const response = await fetch(
    `/api/inventory/transactions/${encodeURIComponent(transactionId)}`,
    {
      cache: "no-store",
      credentials: "same-origin",
      headers: { Accept: "application/json" }
    }
  );
  if (!response.ok) {
    const problem = await readProblem(response);
    throw new InventoryPreparationError(problem.title);
  }
  const etag = response.headers.get("ETag");
  if (!etag || !/^"\d{1,19}"$/.test(etag)) {
    throw new InventoryPreparationError(
      "Máy chủ không trả về phiên bản giao dịch hợp lệ."
    );
  }
  return etag;
}

export class InventoryPreparationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "InventoryPreparationError";
  }
}

async function readProblem(response: Response): Promise<InventoryApiProblem> {
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
          : "Máy chủ chưa chấp nhận giao dịch kho."
    };
  } catch {
    return {
      correlationId: response.headers.get("X-Correlation-Id") ?? undefined,
      title: "Máy chủ chưa chấp nhận giao dịch kho."
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
