import "server-only";

import {
  boundedAssistantUpstreamFetch,
  boundedUpstreamFetch,
  boundedUpstreamStreamFetch
} from "@/server/bff/bounded-upstream-fetch";
import {
  resolveAllowedAnalyticsCommand,
  resolveAllowedMutation,
  resolveAllowedOperation,
  type AllowedAnalyticsCommandName,
  type PathParameterKind,
  type AllowedMutationName,
  type AllowedOperationName
} from "@/server/bff/allowed-operation";
import type { WebEnvironment } from "@/server/config/environment";

const MAX_UPSTREAM_REQUEST_BYTES = 64 * 1024;
const UUID_PATH_PARAMETER =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ROLE_CODE_PATH_VALUES = new Set([
  "DATA_ANALYST",
  "EXECUTIVE",
  "FARM_MANAGER",
  "FIELD_WORKER",
  "INVENTORY_MANAGER",
  "SUPPLIER",
  "TENANT_ADMIN"
]);

type QueryValue = boolean | number | string | null;
type Query = Readonly<Record<string, QueryValue | readonly QueryValue[] | undefined>>;
type PathParameters = Readonly<Record<string, string>>;

export async function executeAllowedOperation(
  env: WebEnvironment,
  operationName: AllowedOperationName,
  accessToken: string,
  correlationId: string,
  query: Query = {},
  pathParameters: PathParameters = {}
): Promise<Response> {
  const operation = resolveAllowedOperation(operationName);
  const url = buildOperationUrl(env, operation, query, pathParameters);
  return boundedUpstreamFetch(url, {
    method: operation.method,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
      "X-Correlation-Id": correlationId
    }
  });
}

export async function executeAllowedFileOperation(
  env: WebEnvironment,
  operationName: "analyticsCostExport",
  accessToken: string,
  correlationId: string,
  query: Query = {}
): Promise<Response> {
  const operation = resolveAllowedOperation(operationName);
  const url = buildOperationUrl(env, operation, query, {});
  return boundedUpstreamStreamFetch(url, {
    method: operation.method,
    headers: {
      Accept: "*/*",
      Authorization: `Bearer ${accessToken}`,
      "X-Correlation-Id": correlationId
    }
  });
}

export async function executeAllowedMutation(
  env: WebEnvironment,
  operationName: AllowedMutationName,
  accessToken: string,
  correlationId: string,
  idempotencyKey: string,
  body: unknown,
  pathParameters: PathParameters,
  ifMatch?: string
): Promise<Response> {
  const operation = resolveAllowedMutation(operationName);
  validateIdempotencyKey(idempotencyKey);
  const validatedIfMatch = validateMutationIfMatch(
    operation.requiresIfMatch === true,
    ifMatch
  );
  const serializedBody = serializeBoundedJsonBody(body);
  const url = new URL(
    interpolatePath(
      operation.path,
      operation.pathParameters,
      pathParameters,
      operation.pathParameterKinds
    ),
    env.backendBaseUrl
  );
  const headers: Record<string, string> = {
    Accept: "application/json",
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
    "Idempotency-Key": idempotencyKey,
    "X-Correlation-Id": correlationId
  };
  if (validatedIfMatch) {
    headers["If-Match"] = validatedIfMatch;
  }
  return boundedUpstreamFetch(url, {
    method: operation.method,
    body: serializedBody,
    headers
  });
}

export async function executeAllowedAnalyticsCommand(
  env: WebEnvironment,
  operationName: AllowedAnalyticsCommandName,
  accessToken: string,
  correlationId: string,
  body: unknown,
  signal?: AbortSignal
): Promise<Response> {
  const operation = resolveAllowedAnalyticsCommand(operationName);
  const serializedBody = serializeBoundedJsonBody(body);
  const url = new URL(operation.path, env.analyticsBaseUrl);
  return boundedAssistantUpstreamFetch(url, {
    method: operation.method,
    body: serializedBody,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
      "X-Correlation-Id": correlationId
    },
    signal
  });
}

function buildOperationUrl(
  env: WebEnvironment,
  operation: ReturnType<typeof resolveAllowedOperation>,
  query: Query,
  pathParameters: PathParameters
): URL {
  const baseUrl =
    operation.service === "backend"
      ? env.backendBaseUrl
      : env.analyticsBaseUrl;
  const url = new URL(
    interpolatePath(
      operation.path,
      operation.pathParameters ?? [],
      pathParameters,
      operation.pathParameterKinds
    ),
    baseUrl
  );
  appendQuery(url, query, operation.queryParameters ?? []);
  return url;
}

function validateIdempotencyKey(idempotencyKey: string): void {
  if (
    typeof idempotencyKey !== "string"
    || !/^[\x21-\x7e]{1,200}$/.test(idempotencyKey)
  ) {
    throw new Error("Invalid upstream idempotency key");
  }
}

function validateMutationIfMatch(
  required: boolean,
  ifMatch: string | undefined
): string | undefined {
  if (!required && ifMatch !== undefined) {
    throw new Error("If-Match is not allowlisted for this upstream mutation");
  }
  if (required && (ifMatch === undefined || !/^"\d{1,19}"$/.test(ifMatch))) {
    throw new Error("Invalid upstream If-Match value");
  }
  return ifMatch;
}

function serializeBoundedJsonBody(body: unknown): string {
  const serializedBody = JSON.stringify(body);
  if (typeof serializedBody !== "string") {
    throw new Error("Invalid upstream JSON body");
  }
  if (new TextEncoder().encode(serializedBody).byteLength > MAX_UPSTREAM_REQUEST_BYTES) {
    throw new Error("Upstream request body exceeded the byte limit");
  }
  return serializedBody;
}

function interpolatePath(
  template: string,
  expectedParameters: readonly string[],
  suppliedParameters: PathParameters,
  parameterKinds: Readonly<Record<string, PathParameterKind>> = {}
): string {
  const suppliedNames = Object.keys(suppliedParameters).sort();
  const expectedNames = [...expectedParameters].sort();
  if (
    suppliedNames.length !== expectedNames.length
    || suppliedNames.some((name, index) => name !== expectedNames[index])
  ) {
    throw new Error("Invalid upstream path parameters");
  }
  let resolved = template;
  for (const name of expectedNames) {
    const value = suppliedParameters[name];
    if (!isValidPathParameter(value, parameterKinds[name] ?? "uuid")) {
      throw new Error("Invalid upstream path parameter value");
    }
    resolved = resolved.replace(`{${name}}`, encodeURIComponent(value));
  }
  if (/[{}]/.test(resolved)) {
    throw new Error("Unresolved upstream path parameter");
  }
  return resolved;
}

function isValidPathParameter(
  value: string | undefined,
  kind: PathParameterKind
): value is string {
  if (!value) return false;
  if (kind === "role-code") return ROLE_CODE_PATH_VALUES.has(value);
  return UUID_PATH_PARAMETER.test(value);
}

function appendQuery(
  url: URL,
  query: Query,
  allowedParameters: readonly string[]
): void {
  const allowedNames = new Set(allowedParameters);
  let entries = 0;
  for (const [name, rawValue] of Object.entries(query)) {
    if (!/^[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)*$/.test(name)) {
      throw new Error("Invalid upstream query parameter name");
    }
    if (!allowedNames.has(name)) {
      throw new Error("Upstream query parameter is not allowlisted");
    }
    const values = Array.isArray(rawValue) ? rawValue : [rawValue];
    for (const value of values) {
      if (value === null || value === undefined) continue;
      entries += 1;
      if (entries > 32) throw new Error("Too many upstream query parameters");
      const normalized = String(value);
      if (normalized.length > 256) {
        throw new Error("Upstream query parameter is too long");
      }
      url.searchParams.append(name, normalized);
    }
  }
}
