import { AuthError } from "@/server/auth/auth-error";
import type { WebEnvironment } from "@/server/config/environment";

export function assertSameOriginMutation(
  request: Request,
  env: WebEnvironment
): void {
  const origin = request.headers.get("origin");
  if (origin !== env.baseUrl.origin) {
    throw new AuthError("invalid_origin", 403, "Yêu cầu khác nguồn đã bị từ chối.");
  }
  if (!["POST", "PUT", "PATCH", "DELETE"].includes(request.method)) {
    throw new AuthError("invalid_request", 405, "Phương thức không hợp lệ.");
  }
}
