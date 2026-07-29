import type { NextRequest } from "next/server";

import {
  authorizeRealtimeAlertRead
} from "@/features/realtime-alerts/realtime-alert-api-security";
import {
  realtimeAlertFeedResponse,
  realtimeAlertRouteErrorResponse
} from "@/features/realtime-alerts/realtime-alert-route-responses";
import { executeAllowedOperation } from "@/server/bff/upstream-client";

export async function GET(request: NextRequest) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeRealtimeAlertRead(request);
    correlationId = context.correlationId;
    const upstream = await executeAllowedOperation(
      context.env,
      "realtimeAlerts",
      context.accessToken,
      context.correlationId,
      {},
      {},
      request.signal
    );
    return realtimeAlertFeedResponse(upstream, context.correlationId);
  } catch (error) {
    return realtimeAlertRouteErrorResponse(error, correlationId);
  }
}
