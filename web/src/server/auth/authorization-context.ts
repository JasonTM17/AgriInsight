import "server-only";

import { cache } from "react";
import { z } from "zod";

import { getCurrentUser } from "@/server/clients/backend";
import type { WebEnvironment } from "@/server/config/environment";

const currentUserSchema = z.object({
  assurance: z.string().optional(),
  displayName: z.string().min(1),
  email: z.string().email(),
  permissions: z.array(z.string()).default([]),
  profileId: z.uuid(),
  roles: z.array(z.string()).default([]),
  tenantCode: z.string().min(1),
  tenantId: z.uuid()
});

export type AuthorizationContext = Readonly<
  Omit<z.infer<typeof currentUserSchema>, "permissions" | "roles"> & {
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
      permissions: new Set(identity.permissions),
      roles: new Set(identity.roles)
    });
  }
);
