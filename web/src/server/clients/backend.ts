import "server-only";

import { AuthError, invalidSessionError } from "@/server/auth/auth-error";
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
    if (response.status === 401) throw invalidSessionError();
    if (response.status === 403) {
      throw new AuthError(
        "authorization_denied",
        403,
        "Tài khoản không có quyền truy cập tài nguyên này."
      );
    }
    throw new Error(`Spring identity request failed with status ${response.status}`);
  }
  return (await response.json()) as CurrentUserResponse;
}
