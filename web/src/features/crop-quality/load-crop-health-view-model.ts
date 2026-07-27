import "server-only";

import { getAnalyticsPayload } from "@/server/clients/analytics";
import type { WebEnvironment } from "@/server/config/environment";

import {
  cropHealthEnvelopeSchema,
  type CropHealthEnvelope
} from "./analytics-evidence-contract";
import type { CropHealthRouteState } from "./crop-health-route-state";

type CropHealthContext = Readonly<{
  accessToken: string;
  correlationId: string;
  env: WebEnvironment;
}>;

export async function loadCropHealthViewModel(
  context: CropHealthContext,
  state: CropHealthRouteState
): Promise<CropHealthEnvelope> {
  const response = await getAnalyticsPayload(
    context.env,
    "analyticsCropHealth",
    context.accessToken,
    context.correlationId,
    {
      farm_code: state.farmCode,
      limit: state.limit,
      offset: state.offset
    }
  );
  return cropHealthEnvelopeSchema.parse(response);
}

export async function loadCropHealthFieldViewModel(
  context: CropHealthContext,
  fieldCode: string
): Promise<CropHealthEnvelope> {
  const response = await getAnalyticsPayload(
    context.env,
    "analyticsCropHealth",
    context.accessToken,
    context.correlationId,
    { field_code: fieldCode, limit: 1, offset: 0 }
  );
  return cropHealthEnvelopeSchema.parse(response);
}
