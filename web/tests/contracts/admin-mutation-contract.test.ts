import { describe, expect, it } from "vitest";

import {
  adminCommandRequiresIfMatch,
  adminMutationCommandSchema
} from "@/features/admin/admin-mutation-contract";

const userKey = "21000000-0000-4000-8000-000000000002";

describe("tenant administration mutation contract", () => {
  it("accepts a bounded user creation command", () => {
    expect(adminMutationCommandSchema.parse({
      displayName: "Nguyễn An",
      email: "",
      issuer: "https://identity.example.test/realms/agri",
      kind: "createUser",
      subject: "oidc-subject"
    })).toEqual({
      displayName: "Nguyễn An",
      email: undefined,
      issuer: "https://identity.example.test/realms/agri",
      kind: "createUser",
      subject: "oidc-subject"
    });
  });

  it("rejects unexpected fields and malformed identifiers", () => {
    expect(adminMutationCommandSchema.safeParse({
      kind: "deactivateUser",
      rawToken: "must-not-pass",
      userKey: "not-a-uuid"
    }).success).toBe(false);
  });

  it("requires optimistic concurrency only for versioned commands", () => {
    const link = adminMutationCommandSchema.parse({
      issuer: "https://identity.example.test",
      kind: "linkIdentity",
      subject: "opaque-subject",
      userKey
    });
    const grant = adminMutationCommandSchema.parse({
      kind: "grantRole",
      roleCode: "DATA_ANALYST",
      userKey
    });

    expect(adminCommandRequiresIfMatch(link)).toBe(false);
    expect(adminCommandRequiresIfMatch(grant)).toBe(true);
  });
});
