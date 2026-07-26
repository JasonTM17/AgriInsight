import "server-only";

import { executeAllowedMutation } from "@/server/bff/upstream-client";

import type { InventoryMutationContext } from "./inventory-api-security";
import { getInventoryTransactionDetail } from "./inventory-generated-client-adapter";
import { reversalInventoryTransactionSchema } from "./inventory-mutation-contract";
import { InventoryApiError } from "./inventory-route-responses";

export async function postInventoryReversal(
  context: InventoryMutationContext,
  input: unknown,
  ifMatch: string
): Promise<Response> {
  const parsed = reversalInventoryTransactionSchema.parse(input);
  const { transactionId, ...payload } = parsed;
  const source = await getInventoryTransactionDetail(context, transactionId);
  if (source.etag !== ifMatch) {
    throw new InventoryApiError(
      "inventory_conflict",
      409,
      "Giao dịch đã thay đổi. Hãy tải lại trước khi đảo."
    );
  }
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
