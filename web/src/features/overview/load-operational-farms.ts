import "server-only";

import { z } from "zod";

import type { OperationalFarm } from "@/features/overview/resolve-analytics-codes";
import { executeAllowedOperation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

const farmSchema = z.object({
  id: z.uuid(),
  code: z.string().trim().min(1).max(64),
  displayName: z.string().trim().min(1).max(160),
  active: z.boolean(),
  version: z.number().int().nonnegative()
});

const farmPageSchema = z.object({
  items: z.array(farmSchema).default([]),
  hasMore: z.boolean().default(false),
  limit: z.number().int().positive().default(100),
  offset: z.number().int().nonnegative().default(0)
});

export async function loadOperationalFarms(
  env: WebEnvironment,
  accessToken: string,
  correlationId: string,
  options: Readonly<{
    active?: boolean;
    search?: string;
  }> = {}
): Promise<readonly OperationalFarm[]> {
  const response = await executeAllowedOperation(
    env,
    "farmCatalog",
    accessToken,
    correlationId,
    {
      active: options.active,
      limit: 100,
      offset: 0,
      search: options.search
    }
  );
  if (!response.ok) {
    throw new Error(`Operational farm request failed with status ${response.status}`);
  }
  const page = farmPageSchema.parse(await response.json());
  if (page.hasMore) {
    throw new Error("Operational farm catalog exceeded the bounded page contract");
  }
  return page.items;
}
