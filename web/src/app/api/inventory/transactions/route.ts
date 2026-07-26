import type { NextRequest } from "next/server";

import {
  authorizeInventoryMutation,
  readBoundedInventoryJson
} from "@/features/inventory/inventory-api-security";
import { toInventoryMutationResponse } from "@/features/inventory/inventory-mutation-response";
import {
  inventoryRouteErrorResponse
} from "@/features/inventory/inventory-route-responses";
import { postInventoryTransaction } from "@/features/inventory/post-inventory-transaction";

export async function POST(request: NextRequest) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeInventoryMutation(request);
    correlationId = context.correlationId;
    const body = await readBoundedInventoryJson(request);
    const upstream = await postInventoryTransaction(context, body);
    return toInventoryMutationResponse(upstream, context.correlationId);
  } catch (error) {
    return inventoryRouteErrorResponse(error, correlationId);
  }
}
