import "server-only";

import { boundedUpstreamFetch } from "@/server/bff/bounded-upstream-fetch";
import {
  resolveAllowedOperation,
  type AllowedOperationName
} from "@/server/bff/allowed-operation";
import type { WebEnvironment } from "@/server/config/environment";

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
  const baseUrl =
    operation.service === "backend"
      ? env.backendBaseUrl
      : env.analyticsBaseUrl;
  const url = new URL(
    interpolatePath(
      operation.path,
      operation.pathParameters ?? [],
      pathParameters
    ),
    baseUrl
  );
  appendQuery(url, query, operation.queryParameters ?? []);
  return boundedUpstreamFetch(url, {
    method: operation.method,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
      "X-Correlation-Id": correlationId
    }
  });
}

function interpolatePath(
  template: string,
  expectedParameters: readonly string[],
  suppliedParameters: PathParameters
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
    if (
      !value
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
    ) {
      throw new Error("Invalid upstream path parameter value");
    }
    resolved = resolved.replace(`{${name}}`, encodeURIComponent(value));
  }
  if (/[{}]/.test(resolved)) {
    throw new Error("Unresolved upstream path parameter");
  }
  return resolved;
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
