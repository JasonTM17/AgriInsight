import "server-only";

import { executeAllowedMutation } from "@/server/bff/upstream-client";

import type { InventoryMutationContext } from "./inventory-api-security";
import { reversalInventoryTransactionSchema } from "./inventory-mutation-contract";

export async function postInventoryReversal(
  context: InventoryMutationContext,
  input: unknown,
  ifMatch: string
): Promise<Response> {
  const parsed = reversalInventoryTransactionSchema.parse(input);
  const { transactionId, ...payload } = parsed;
  return executeAllowedMutation(
    context.env,
    "inventoryTransactionReversal",
    context.accessToken,
    context.correlationId,
    context.idempotencyKey,
    { ...payload, reasonCode: "WEB_REVERSAL_ENTRY" },
    { id: transactionId },
    ifMatch
  );
}
