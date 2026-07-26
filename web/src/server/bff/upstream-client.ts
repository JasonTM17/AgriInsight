import "server-only";

import { boundedUpstreamFetch } from "@/server/bff/bounded-upstream-fetch";
import {
  resolveAllowedOperation,
  type AllowedOperationName
} from "@/server/bff/allowed-operation";
import type { WebEnvironment } from "@/server/config/environment";

type QueryValue = boolean | number | string | null;
type Query = Readonly<Record<string, QueryValue | readonly QueryValue[] | undefined>>;

export async function executeAllowedOperation(
  env: WebEnvironment,
  operationName: AllowedOperationName,
  accessToken: string,
  correlationId: string,
  query: Query = {}
): Promise<Response> {
  const operation = resolveAllowedOperation(operationName);
  const baseUrl =
    operation.service === "backend"
      ? env.backendBaseUrl
      : env.analyticsBaseUrl;
  const url = new URL(operation.path, baseUrl);
  appendQuery(url, query);
  return boundedUpstreamFetch(url, {
    method: operation.method,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
      "X-Correlation-Id": correlationId
    }
  });
}

function appendQuery(url: URL, query: Query): void {
  let entries = 0;
  for (const [name, rawValue] of Object.entries(query)) {
    if (!/^[A-Za-z][A-Za-z0-9]*$/.test(name)) {
      throw new Error("Invalid upstream query parameter name");
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
