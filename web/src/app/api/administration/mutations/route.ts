import type { NextRequest } from "next/server";

import {
  assertAdminCommandPermission,
  authorizeAdminMutation,
  readBoundedAdminJson
} from "@/features/admin/admin-api-security";
import {
  adminCommandRequiresIfMatch,
  adminMutationCommandSchema
} from "@/features/admin/admin-mutation-contract";
import {
  adminRouteErrorResponse,
  readAdminIfMatch,
  toAdminMutationResponse
} from "@/features/admin/admin-route-responses";
import { executeAdminMutation } from "@/features/admin/execute-admin-mutation";

export async function POST(request: NextRequest) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeAdminMutation(request);
    correlationId = context.correlationId;
    const command = adminMutationCommandSchema.parse(
      await readBoundedAdminJson(request)
    );
    assertAdminCommandPermission(context, command);
    const ifMatch = readAdminIfMatch(
      request.headers.get("If-Match"),
      adminCommandRequiresIfMatch(command)
    );
    const upstream = await executeAdminMutation(context, command, ifMatch);
    return toAdminMutationResponse(upstream, context.correlationId);
  } catch (error) {
    return adminRouteErrorResponse(error, correlationId);
  }
}
