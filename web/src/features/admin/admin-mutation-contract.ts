import { z } from "zod";

const uuid = z.uuid();
const optionalEmail = z.preprocess(
  (value) => typeof value === "string" && value.trim() === "" ? undefined : value,
  z.email().max(320).optional()
);

export const adminRoleCodeSchema = z.enum([
  "DATA_ANALYST",
  "EXECUTIVE",
  "FARM_MANAGER",
  "FIELD_WORKER",
  "INVENTORY_MANAGER",
  "SUPPLIER",
  "TENANT_ADMIN"
]);
export type AdminRoleCode = z.output<typeof adminRoleCodeSchema>;

const createUser = z.object({
  displayName: z.string().trim().min(1).max(200),
  email: optionalEmail,
  issuer: z.url().max(2048),
  kind: z.literal("createUser"),
  subject: z.string().trim().min(1).max(512)
}).strict();

const userLifecycle = z.object({
  kind: z.enum(["deactivateUser", "reactivateUser"]),
  userKey: uuid
}).strict();

const roleMutation = z.object({
  kind: z.enum(["grantRole", "revokeRole"]),
  roleCode: adminRoleCodeSchema,
  userKey: uuid
}).strict();

const linkIdentity = z.object({
  issuer: z.url().max(2048),
  kind: z.literal("linkIdentity"),
  subject: z.string().trim().min(1).max(512),
  userKey: uuid
}).strict();

const unlinkIdentity = z.object({
  identityKey: uuid,
  kind: z.literal("unlinkIdentity"),
  userKey: uuid
}).strict();

const farmMutation = z.object({
  farmKey: uuid,
  kind: z.literal("grantFarm"),
  userKey: uuid
}).strict().or(z.object({
  assignmentKey: uuid,
  kind: z.literal("revokeFarm")
}).strict());

const warehouseMutation = z.object({
  kind: z.literal("grantWarehouse"),
  userKey: uuid,
  warehouseKey: uuid
}).strict().or(z.object({
  assignmentKey: uuid,
  kind: z.literal("revokeWarehouse")
}).strict());

const activityMutation = z.object({
  activityKey: uuid,
  employeeKey: uuid,
  kind: z.literal("grantActivity")
}).strict().or(z.object({
  activityKey: uuid,
  assignmentKey: uuid,
  kind: z.literal("revokeActivity")
}).strict());

export const adminMutationCommandSchema = z.union([
  createUser,
  userLifecycle,
  roleMutation,
  linkIdentity,
  unlinkIdentity,
  farmMutation,
  warehouseMutation,
  activityMutation
]);

export type AdminMutationCommand = z.output<
  typeof adminMutationCommandSchema
>;

const VERSIONED_COMMANDS: ReadonlySet<AdminMutationCommand["kind"]> = new Set([
  "deactivateUser",
  "grantActivity",
  "grantFarm",
  "grantRole",
  "grantWarehouse",
  "reactivateUser",
  "revokeActivity",
  "revokeFarm",
  "revokeRole",
  "revokeWarehouse"
]);

export function adminCommandRequiresIfMatch(
  command: AdminMutationCommand
): boolean {
  return VERSIONED_COMMANDS.has(command.kind);
}
