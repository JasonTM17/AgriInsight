import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { z } from "zod";

import { authorizeInventoryRead } from "@/features/inventory/inventory-api-security";
import { getInventoryTransactionDetail } from "@/features/inventory/inventory-generated-client-adapter";
import { inventoryRouteErrorResponse } from "@/features/inventory/inventory-route-responses";

const paramsSchema = z.object({ transactionId: z.uuid() }).strict();

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ transactionId: string }> }
) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeInventoryRead(request);
    correlationId = context.correlationId;
    const { transactionId } = paramsSchema.parse(await params);
    const detail = await getInventoryTransactionDetail(context, transactionId);
    return NextResponse.json(detail.transaction, {
      headers: {
        "Cache-Control": "no-store",
        ETag: detail.etag,
        "X-Correlation-Id": context.correlationId
      }
    });
  } catch (error) {
    return inventoryRouteErrorResponse(error, correlationId);
  }
}
