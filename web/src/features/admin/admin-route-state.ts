import { z } from "zod";

import {
  ADMIN_MAX_OFFSET,
  ADMIN_PAGE_SIZE
} from "./admin-contract-schemas";

const singleValue = z.union([z.string(), z.array(z.string()).length(1)])
  .transform((value) => Array.isArray(value) ? value[0] : value);

const booleanFilter = singleValue.pipe(z.enum(["active", "all", "inactive"]));
const offset = singleValue.pipe(
  z.coerce.number().int().min(0).max(ADMIN_MAX_OFFSET)
).refine((value) => value % ADMIN_PAGE_SIZE === 0);
const search = singleValue.pipe(z.string().trim().min(1).max(120));
const code = singleValue.pipe(z.string().regex(/^[A-Za-z][A-Za-z0-9_-]{0,99}$/));

export function parseAdminDirectoryState(
  input: Readonly<Record<string, string | readonly string[] | undefined>>
) {
  const parsed = z.object({
    offset: offset.optional().default(0),
    search: search.optional(),
    status: booleanFilter.optional().default("active")
  }).strict().safeParse(input);
  if (!parsed.success) return null;
  return {
    active: parsed.data.status === "all"
      ? undefined
      : parsed.data.status === "active",
    offset: parsed.data.offset,
    search: parsed.data.search,
    status: parsed.data.status
  } as const;
}

export function parseAdminAuditState(
  input: Readonly<Record<string, string | readonly string[] | undefined>>
) {
  const parsed = z.object({
    action: code.optional(),
    offset: offset.optional().default(0),
    outcome: singleValue.pipe(
      z.enum(["CONFLICT", "DENIED", "SUCCEEDED"])
    ).optional(),
    targetType: code.optional()
  }).strict().safeParse(input);
  return parsed.success ? parsed.data : null;
}
