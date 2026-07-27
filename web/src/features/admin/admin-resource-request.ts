import "server-only";

import type { ZodType } from "zod";

import type { AllowedOperationName } from "@/server/bff/allowed-operation";
import { executeAllowedOperation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

export type AdminReadContext = Readonly<{
  accessToken: string;
  correlationId: string;
  env: WebEnvironment;
}>;

export type AdminReadErrorKind =
  | "denied"
  | "not_found"
  | "unauthenticated"
  | "unavailable";

export class AdminReadError extends Error {
  constructor(
    readonly kind: AdminReadErrorKind,
    readonly status: 401 | 403 | 404 | 502
  ) {
    super(kind);
    this.name = "AdminReadError";
  }
}

export async function getAdminJson<Output>(
  context: AdminReadContext,
  operation: AllowedOperationName,
  schema: ZodType<Output>,
  query: Readonly<Record<string, boolean | number | string | undefined>>,
  pathParameters: Readonly<Record<string, string>> = {}
): Promise<Output> {
  const response = await requestAdminResource(
    context,
    operation,
    query,
    pathParameters
  );
  try {
    return schema.parse(await response.json());
  } catch {
    throw new AdminReadError("unavailable", 502);
  }
}

export async function requestAdminResource(
  context: AdminReadContext,
  operation: AllowedOperationName,
  query: Readonly<Record<string, boolean | number | string | undefined>>,
  pathParameters: Readonly<Record<string, string>>
): Promise<Response> {
  let response: Response;
  try {
    response = await executeAllowedOperation(
      context.env,
      operation,
      context.accessToken,
      context.correlationId,
      compact(query),
      pathParameters
    );
  } catch {
    throw new AdminReadError("unavailable", 502);
  }
  if (!response.ok) throw adminReadErrorForStatus(response.status);
  return response;
}

function compact(
  query: Readonly<Record<string, boolean | number | string | undefined>>
): Readonly<Record<string, boolean | number | string>> {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== undefined)
  ) as Readonly<Record<string, boolean | number | string>>;
}

function adminReadErrorForStatus(status: number): AdminReadError {
  if (status === 401) return new AdminReadError("unauthenticated", 401);
  if (status === 403) return new AdminReadError("denied", 403);
  if (status === 404) return new AdminReadError("not_found", 404);
  return new AdminReadError("unavailable", 502);
}
