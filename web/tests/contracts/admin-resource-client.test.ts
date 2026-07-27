import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  AdminReadError,
  getAdminUser,
  getAdminUserPage
} from "@/features/admin/admin-resource-client";
import { executeAllowedOperation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

vi.mock("@/server/bff/upstream-client", async (importOriginal) => {
  const original = await importOriginal<
    typeof import("@/server/bff/upstream-client")
  >();
  return { ...original, executeAllowedOperation: vi.fn() };
});

const context = {
  accessToken: "server-held-token",
  correlationId: "correlation-1",
  env: {
    backendBaseUrl: new URL("http://127.0.0.1:8080")
  } as WebEnvironment
};

describe("tenant administration resource client", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedOperation).mockReset();
  });

  it("requests a bounded user page through the frozen users resource", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json({
        hasMore: false,
        items: [{
          active: true,
          displayName: "Nguyễn An",
          email: "an@example.com",
          id: "21000000-0000-4000-8000-000000000002",
          version: 3
        }],
        limit: 50,
        offset: 0
      })
    );

    const page = await getAdminUserPage(context, {
      active: true,
      offset: 0,
      search: "Nguyễn"
    });

    expect(page.items[0]?.displayName).toBe("Nguyễn An");
    expect(executeAllowedOperation).toHaveBeenCalledWith(
      context.env,
      "adminUsers",
      context.accessToken,
      context.correlationId,
      { active: true, limit: 50, offset: 0, search: "Nguyễn" },
      {}
    );
  });

  it.each([
    [401, "unauthenticated"],
    [403, "denied"],
    [404, "not_found"],
    [409, "unavailable"]
  ] as const)("preserves status %s as %s", async (status, kind) => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      new Response(null, { status })
    );

    await expect(
      getAdminUser(context, "21000000-0000-4000-8000-000000000002")
    ).rejects.toMatchObject<Partial<AdminReadError>>({ kind });
  });

  it("requires a strong ETag on the subject detail", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json({
        active: true,
        displayName: "Nguyễn An",
        id: "21000000-0000-0000-0000-000000000002",
        version: 3
      })
    );

    await expect(
      getAdminUser(context, "21000000-0000-4000-8000-000000000002")
    ).rejects.toMatchObject<Partial<AdminReadError>>({
      kind: "unavailable",
      status: 502
    });
  });
});
