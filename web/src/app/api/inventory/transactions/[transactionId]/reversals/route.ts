import type { NextRequest } from "next/server";
import { z } from "zod";

import {
  authorizeInventoryMutation,
  readBoundedInventoryJson
} from "@/features/inventory/inventory-api-security";
import { toInventoryMutationResponse } from "@/features/inventory/inventory-mutation-response";
import {
  InventoryApiError,
  inventoryRouteErrorResponse
} from "@/features/inventory/inventory-route-responses";
import { postInventoryReversal } from "@/features/inventory/post-inventory-reversal";

const paramsSchema = z.object({ transactionId: z.uuid() }).strict();
const ifMatchSchema = z.string().regex(/^"\d{1,19}"$/);

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ transactionId: string }> }
) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeInventoryMutation(request);
    correlationId = context.correlationId;
    const { transactionId } = paramsSchema.parse(await params);
    const ifMatch = parseIfMatch(request.headers.get("If-Match"));
    const body = await readBoundedInventoryJson(request);
    const input = isRecord(body)
      ? { ...body, transactionId }
      : { transactionId };
    const upstream = await postInventoryReversal(context, input, ifMatch);
    return toInventoryMutationResponse(upstream, context.correlationId);
  } catch (error) {
    return inventoryRouteErrorResponse(error, correlationId);
  }
}

function parseIfMatch(value: string | null): string {
  const parsed = ifMatchSchema.safeParse(value);
  if (!parsed.success) {
    throw new InventoryApiError(
      "invalid_if_match",
      400,
      "Yêu cầu thiếu phiên bản giao dịch hợp lệ."
    );
  }
  return parsed.data;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
