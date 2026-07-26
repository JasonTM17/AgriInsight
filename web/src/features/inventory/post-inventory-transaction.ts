import "server-only";

import { executeAllowedMutation } from "@/server/bff/upstream-client";

import {
  assertVisibleWarehouse,
  type InventoryMutationContext
} from "./inventory-api-security";
import { postInventoryTransactionSchema } from "./inventory-mutation-contract";

export async function postInventoryTransaction(
  context: InventoryMutationContext,
  input: unknown
): Promise<Response> {
  const parsed = postInventoryTransactionSchema.parse(input);
  await assertVisibleWarehouse(context, parsed.warehouseId);
  const reasonCode = parsed.kind === "RECEIPT"
    ? "WEB_RECEIPT_ENTRY"
    : "WEB_ISSUE_ENTRY";
  return executeAllowedMutation(
    context.env,
    "inventoryTransactionPost",
    context.accessToken,
    context.correlationId,
    context.idempotencyKey,
    { ...parsed, reasonCode },
    {}
  );
}
