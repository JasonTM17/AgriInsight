import type { NextRequest } from "next/server";

import {
  authorizeRealtimeAlertMutation,
  readBoundedRealtimeAlertJson
} from "@/features/realtime-alerts/realtime-alert-api-security";
import {
  realtimeAlertAcknowledgementBodySchema,
  realtimeAlertParamsSchema
} from "@/features/realtime-alerts/realtime-alert-contract";
import {
  realtimeAlertAcknowledgementResponse,
  realtimeAlertRouteErrorResponse
} from "@/features/realtime-alerts/realtime-alert-route-responses";
import { executeAllowedMutation } from "@/server/bff/upstream-client";

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ alertId: string }> }
) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeRealtimeAlertMutation(request);
    correlationId = context.correlationId;
    const body = realtimeAlertAcknowledgementBodySchema.parse(
      await readBoundedRealtimeAlertJson(request)
    );
    const { alertId } = realtimeAlertParamsSchema.parse(await params);
    const upstream = await executeAllowedMutation(
      context.env,
      "realtimeAlertAcknowledge",
      context.accessToken,
      context.correlationId,
      context.idempotencyKey,
      body,
      { id: alertId },
      undefined,
      request.signal
    );
    return realtimeAlertAcknowledgementResponse(
      upstream,
      context.correlationId
    );
  } catch (error) {
    return realtimeAlertRouteErrorResponse(error, correlationId);
  }
}
