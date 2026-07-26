import "server-only";

import { cache } from "react";
import { z } from "zod";

import { getCurrentUser } from "@/server/clients/backend";
import type { WebEnvironment } from "@/server/config/environment";

const currentUserSchema = z.object({
  assurance: z.string().nullable().optional(),
  displayName: z.string().min(1).nullable().optional(),
  email: z.string().email().nullable().optional(),
  permissions: z.array(z.string()).default([]),
  profileId: z.uuid(),
  roles: z.array(z.string()).default([]),
  tenantCode: z.string().min(1),
  tenantId: z.uuid()
});

export type AuthorizationContext = Readonly<
  Omit<
    z.infer<typeof currentUserSchema>,
    "assurance" | "displayName" | "email" | "permissions" | "roles"
  > & {
    assurance: string | null;
    displayName: string;
    email: string | null;
    permissions: ReadonlySet<string>;
    roles: ReadonlySet<string>;
  }
>;

export const getAuthorizationContext = cache(
  async (
    env: WebEnvironment,
    accessToken: string,
    correlationId: string
  ): Promise<AuthorizationContext> => {
    const identity = currentUserSchema.parse(
      await getCurrentUser(env, accessToken, correlationId)
    );
    return Object.freeze({
      ...identity,
      assurance: identity.assurance ?? null,
      displayName: identity.displayName ?? "Người dùng AgriInsight",
      email: identity.email ?? null,
      permissions: new Set(identity.permissions),
      roles: new Set(identity.roles)
    });
  }
);
