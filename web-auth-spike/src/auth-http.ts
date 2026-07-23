import { randomUUID } from "node:crypto";

import { NextResponse } from "next/server";

import { sanitizedAuthError } from "./auth-errors.ts";

export function authErrorResponse(error: unknown): NextResponse {
  const safe = sanitizedAuthError(error);
  return NextResponse.json(
    {
      type: "about:blank",
      title: safe.message,
      status: safe.status,
      code: safe.code,
    },
    {
      status: safe.status,
      headers: {
        "Cache-Control": "no-store",
        "Content-Type": "application/problem+json",
        "X-Correlation-Id": randomUUID(),
      },
    },
  );
}

export const NO_STORE_HEADERS = Object.freeze({
  "Cache-Control": "no-store",
});
