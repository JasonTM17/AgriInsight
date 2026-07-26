import "server-only";

import {
  ALLOWED_OPERATIONS,
  type AllowedOperationName
} from "@/server/bff/allowed-operation";
import { executeAllowedOperation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";
import type { paths as AnalyticsPaths } from "@/server/generated/analytics/schema";

type AnalyticsOperation = Extract<
  AllowedOperationName,
  `analytics${string}`
>;

type AnalyticsPath<Operation extends AnalyticsOperation> =
  (typeof ALLOWED_OPERATIONS)[Operation]["path"] & keyof AnalyticsPaths;

export type AnalyticsResponse<Operation extends AnalyticsOperation> =
  AnalyticsPaths[AnalyticsPath<Operation>] extends {
    readonly get: {
      readonly responses: {
        readonly 200: {
          readonly content: {
            readonly "application/json": infer ResponseBody;
          };
        };
      };
    };
  }
    ? ResponseBody
    : never;

export type AnalyticsQuery<Operation extends AnalyticsOperation> =
  AnalyticsPaths[AnalyticsPath<Operation>] extends {
    readonly get: {
      readonly parameters: {
        readonly query?: infer Query;
      };
    };
  }
    ? Query
    : never;

export async function getAnalyticsPayload<
  Operation extends AnalyticsOperation
>(
  env: WebEnvironment,
  operation: Operation,
  accessToken: string,
  correlationId: string,
  query?: AnalyticsQuery<Operation>
): Promise<AnalyticsResponse<Operation>> {
  const response = await executeAllowedOperation(
    env,
    operation,
    accessToken,
    correlationId,
    query as Readonly<
      Record<
        string,
        boolean | number | string | null | readonly (boolean | number | string | null)[] | undefined
      >
    >
  );
  if (!response.ok) {
    throw new Error(`Analytics request failed with status ${response.status}`);
  }
  return (await response.json()) as AnalyticsResponse<Operation>;
}
