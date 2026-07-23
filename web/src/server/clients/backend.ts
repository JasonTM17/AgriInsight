import "server-only";

import type { components } from "@/server/generated/backend/schema";
import { executeAllowedOperation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

export type CurrentUserResponse = components["schemas"]["CurrentUserResponse"];

export async function getCurrentUser(
  env: WebEnvironment,
  accessToken: string,
  correlationId: string
): Promise<CurrentUserResponse> {
  const response = await executeAllowedOperation(
    env,
    "currentUser",
    accessToken,
    correlationId
  );
  if (!response.ok) {
    throw new Error(`Spring identity request failed with status ${response.status}`);
  }
  return (await response.json()) as CurrentUserResponse;
}
