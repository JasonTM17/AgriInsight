import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  loadAdminAudit,
  loadAdminSubject
} from "@/features/admin/admin-read-model";
import {
  getAdminAuditPage,
  getAdminScopeCatalogs,
  getAdminUser,
  getAdminUserPage,
  getAdminUserRelations
} from "@/features/admin/admin-resource-client";
import type { WebEnvironment } from "@/server/config/environment";

vi.mock("@/features/admin/admin-resource-client", async (importOriginal) => {
  const original = await importOriginal<
    typeof import("@/features/admin/admin-resource-client")
  >();
  return {
    ...original,
    getAdminAuditPage: vi.fn(),
    getAdminScopeCatalogs: vi.fn(),
    getAdminUser: vi.fn(),
    getAdminUserPage: vi.fn(),
    getAdminUserRelations: vi.fn()
  };
});

const context = {
  accessToken: "server-held-token",
  correlationId: "correlation-1",
  env: {} as WebEnvironment
};
const userKey = "21000000-0000-0000-0000-000000000002";

describe("safe tenant administration read model", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("maps provider and assignment records without exposing issuer or subject", async () => {
    vi.mocked(getAdminUser).mockResolvedValue({
      etag: "\"3\"",
      user: {
        active: true,
        displayName: "Nguyễn An",
        email: "an@example.test",
        id: userKey,
        version: 3
      }
    });
    vi.mocked(getAdminUserRelations).mockResolvedValue({
      externalIdentities: [{
        active: true,
        id: "22000000-0000-0000-0000-000000000002",
        issuer: "https://identity.internal.example/realms/demo",
        version: 1
      }],
      farmAssignments: [{
        active: true,
        farmId: "31000000-0000-0000-0000-000000000001",
        id: "32000000-0000-0000-0000-000000000001",
        userProfileId: userKey,
        version: 0
      }],
      roles: [{
        active: true,
        id: "23000000-0000-0000-0000-000000000002",
        profileId: userKey,
        roleCode: "DATA_ANALYST",
        version: 0
      }],
      warehouseAssignments: []
    });
    vi.mocked(getAdminScopeCatalogs).mockResolvedValue({
      farms: [{
        active: true,
        code: "FARM-001",
        displayName: "Nông trại An Phú",
        id: "31000000-0000-0000-0000-000000000001",
        version: 0
      }],
      warehouses: []
    });

    const subject = await loadAdminSubject(context, userKey);
    const rendered = JSON.stringify(subject);

    expect(subject.providerLinks[0]?.providerLabel).toBe(
      "Nhà cung cấp OIDC đã xác minh"
    );
    expect(subject.assignments[0]?.scopeLabel).toBe("Nông trại An Phú");
    expect(rendered).not.toContain("identity.internal.example");
    expect(rendered).not.toContain("subject");
    expect(rendered).not.toContain("issuer");
  });

  it("resolves bounded audit actors and targets to display-safe labels", async () => {
    vi.mocked(getAdminAuditPage).mockResolvedValue({
      hasMore: false,
      items: [{
        action: "USER_DEACTIVATE",
        actorProfileId: userKey,
        actorType: "USER",
        correlationId: "correlation-1",
        id: "24000000-0000-0000-0000-000000000002",
        occurredAt: "2026-07-27T02:00:00Z",
        outcome: "SUCCEEDED",
        reasonCode: "ACCESS_REVIEW",
        targetId: userKey,
        targetType: "USER_PROFILE"
      }],
      limit: 50,
      offset: 0
    });
    vi.mocked(getAdminUserPage).mockResolvedValue({
      hasMore: false,
      items: [{
        active: true,
        displayName: "Nguyễn An",
        email: null,
        id: userKey,
        version: 3
      }],
      limit: 50,
      offset: 0
    });

    const audit = await loadAdminAudit(context, { offset: 0 });

    expect(audit.entries[0]).toMatchObject({
      actionLabel: "User deactivate",
      actorLabel: "Nguyễn An",
      reasonLabel: "Access review",
      targetLabel: "Nguyễn An"
    });
  });
});
