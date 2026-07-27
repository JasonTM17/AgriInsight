import { z } from "zod";

export const ADMIN_PAGE_SIZE = 50;
export const ADMIN_MAX_OFFSET = 10_000;

const uuid = z.uuid();
const version = z.number().int().nonnegative();
const label = z.string().trim().min(1).max(300);
const optionalUuid = uuid.nullish();
const optionalCode = z.string().trim().min(1).max(100).nullish();
const page = {
  hasMore: z.boolean(),
  limit: z.number().int().min(1).max(100),
  offset: z.number().int().min(0).max(ADMIN_MAX_OFFSET)
};

export const adminUserSchema = z.object({
  active: z.boolean(),
  displayName: label,
  email: z.email().nullish(),
  id: uuid,
  version
}).strict();

export const adminUserPageSchema = z.object({
  ...page,
  items: z.array(adminUserSchema).max(100)
}).strict();

export const adminRoleSchema = z.object({
  active: z.boolean(),
  id: uuid,
  profileId: uuid,
  roleCode: z.enum([
    "DATA_ANALYST",
    "EXECUTIVE",
    "FARM_MANAGER",
    "FIELD_WORKER",
    "INVENTORY_MANAGER",
    "SUPPLIER",
    "TENANT_ADMIN"
  ]),
  version
}).strict();

export const adminRolePageSchema = z.object({
  ...page,
  items: z.array(adminRoleSchema).max(100)
}).strict();

export const adminExternalIdentitySchema = z.object({
  active: z.boolean(),
  id: uuid,
  issuer: z.url(),
  version
}).strict();

export const adminExternalIdentityPageSchema = z.object({
  ...page,
  items: z.array(adminExternalIdentitySchema).max(100)
}).strict();

export const adminFarmAssignmentSchema = z.object({
  active: z.boolean(),
  farmId: uuid,
  id: uuid,
  userProfileId: uuid,
  version
}).strict();

export const adminFarmAssignmentPageSchema = z.object({
  ...page,
  items: z.array(adminFarmAssignmentSchema).max(100)
}).strict();

export const adminWarehouseAssignmentSchema = z.object({
  active: z.boolean(),
  id: uuid,
  revokedAt: z.iso.datetime({ offset: true }).nullish(),
  userProfileId: uuid,
  version,
  warehouseId: uuid
}).strict();

export const adminWarehouseAssignmentPageSchema = z.object({
  ...page,
  items: z.array(adminWarehouseAssignmentSchema).max(100)
}).strict();

export const adminAuditEntrySchema = z.object({
  action: label,
  actorProfileId: optionalUuid,
  actorType: label,
  correlationId: optionalCode,
  id: uuid,
  occurredAt: z.iso.datetime({ offset: true }),
  outcome: z.enum(["CONFLICT", "DENIED", "SUCCEEDED"]),
  reasonCode: optionalCode,
  targetId: optionalUuid,
  targetType: label
}).strict();

export const adminAuditPageSchema = z.object({
  ...page,
  items: z.array(adminAuditEntrySchema).max(100)
}).strict();

const namedResource = z.object({
  active: z.boolean(),
  code: label,
  displayName: label,
  id: uuid,
  version
}).strict();

export const adminFarmCatalogPageSchema = z.object({
  ...page,
  items: z.array(namedResource).max(100)
}).strict();

export const adminWarehouseCatalogPageSchema = z.object({
  ...page,
  items: z.array(
    namedResource.extend({
      locationText: z.string().trim().max(500).nullish()
    }).strict()
  ).max(100)
}).strict();

export type AdminAuditEntry = z.output<typeof adminAuditEntrySchema>;
export type AdminAuditPage = z.output<typeof adminAuditPageSchema>;
export type AdminExternalIdentity = z.output<typeof adminExternalIdentitySchema>;
export type AdminFarmAssignment = z.output<typeof adminFarmAssignmentSchema>;
export type AdminNamedResource = z.output<typeof namedResource>;
export type AdminRole = z.output<typeof adminRoleSchema>;
export type AdminUser = z.output<typeof adminUserSchema>;
export type AdminUserPage = z.output<typeof adminUserPageSchema>;
export type AdminWarehouseAssignment = z.output<
  typeof adminWarehouseAssignmentSchema
>;
