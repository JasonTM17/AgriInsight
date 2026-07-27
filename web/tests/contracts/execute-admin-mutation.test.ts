import { beforeEach, describe, expect, it, vi } from "vitest";

import { executeAdminMutation } from "@/features/admin/execute-admin-mutation";
import { executeAllowedMutation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

vi.mock("@/server/bff/upstream-client", () => ({
  executeAllowedMutation: vi.fn()
}));

const context = {
  accessToken: "server-held-token",
  correlationId: "correlation-1",
  env: {} as WebEnvironment,
  idempotencyKey: "idempotency-1",
  permissions: new Set<string>(),
  roles: new Set<string>()
};

describe("tenant administration mutation dispatcher", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(executeAllowedMutation).mockResolvedValue(
      Response.json({}, { status: 200 })
    );
  });

  it("maps lifecycle commands to exact allowlisted operations", async () => {
    await executeAdminMutation(context, {
      kind: "deactivateUser",
      userKey: "21000000-0000-4000-8000-000000000002"
    }, "\"3\"");

    expect(executeAllowedMutation).toHaveBeenCalledWith(
      context.env,
      "adminUserDeactivate",
      context.accessToken,
      context.correlationId,
      context.idempotencyKey,
      { reasonCode: "WEB_ADMIN_USER_DEACTIVATE" },
      { id: "21000000-0000-4000-8000-000000000002" },
      "\"3\""
    );
  });

  it("keeps OIDC subject server-bound and maps only the exact identity route", async () => {
    await executeAdminMutation(context, {
      issuer: "https://identity.example.test",
      kind: "linkIdentity",
      subject: "opaque-subject",
      userKey: "21000000-0000-4000-8000-000000000002"
    });

    expect(executeAllowedMutation).toHaveBeenCalledWith(
      context.env,
      "adminUserLinkIdentity",
      context.accessToken,
      context.correlationId,
      context.idempotencyKey,
      {
        issuer: "https://identity.example.test",
        reasonCode: "WEB_ADMIN_IDENTITY_LINK",
        subject: "opaque-subject"
      },
      { id: "21000000-0000-4000-8000-000000000002" },
      undefined
    );
  });

  it("uses the fixed role-code path for revoke", async () => {
    await executeAdminMutation(context, {
      kind: "revokeRole",
      roleCode: "DATA_ANALYST",
      userKey: "21000000-0000-4000-8000-000000000002"
    }, "\"2\"");

    expect(vi.mocked(executeAllowedMutation).mock.calls[0]?.[1]).toBe(
      "adminRoleRevoke"
    );
    expect(vi.mocked(executeAllowedMutation).mock.calls[0]?.[6]).toEqual({
      id: "21000000-0000-4000-8000-000000000002",
      roleCode: "DATA_ANALYST"
    });
  });
});
