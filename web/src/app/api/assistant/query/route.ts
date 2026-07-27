import type { NextRequest } from "next/server";

import {
  assistantRouteErrorResponse,
  assistantUpstreamResponse
} from "@/features/assistant/assistant-route-responses";
import {
  authorizeAssistantQuery,
  readBoundedAssistantJson
} from "@/features/assistant/assistant-api-security";
import { assistantQuerySchema } from "@/features/assistant/assistant-contract";
import { executeAllowedAnalyticsCommand } from "@/server/bff/upstream-client";

export async function POST(request: NextRequest) {
  let correlationId: string | undefined;
  try {
    const context = await authorizeAssistantQuery(request);
    correlationId = context.correlationId;
    const query = assistantQuerySchema.parse(
      await readBoundedAssistantJson(request)
    );
    const upstream = await executeAllowedAnalyticsCommand(
      context.env,
      "analyticsAssistantQuery",
      context.accessToken,
      context.correlationId,
      query,
      request.signal
    );
    return assistantUpstreamResponse(upstream, context.correlationId);
  } catch (error) {
    return assistantRouteErrorResponse(error, correlationId);
  }
}
