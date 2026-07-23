import "server-only";

import type { AllowedOperationName } from "@/server/bff/allowed-operation";
import { executeAllowedOperation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

type AnalyticsOperation = Extract<
  AllowedOperationName,
  `analytics${string}`
>;

export async function getAnalyticsPayload<T>(
  env: WebEnvironment,
  operation: AnalyticsOperation,
  accessToken: string,
  correlationId: string,
  query?: Readonly<Record<string, string | readonly string[] | undefined>>
): Promise<T> {
  const response = await executeAllowedOperation(
    env,
    operation,
    accessToken,
    correlationId,
    query
  );
  if (!response.ok) {
    throw new Error(`Analytics request failed with status ${response.status}`);
  }
  return (await response.json()) as T;
}
