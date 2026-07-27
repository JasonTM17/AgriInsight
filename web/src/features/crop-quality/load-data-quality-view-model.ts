import "server-only";

import { getAnalyticsPayload } from "@/server/clients/analytics";
import type { WebEnvironment } from "@/server/config/environment";

import {
  dataQualityEnvelopeSchema,
  type DataQualityEnvelope
} from "./analytics-evidence-contract";

export async function loadDataQualityViewModel({
  env,
  accessToken,
  correlationId
}: Readonly<{
  accessToken: string;
  correlationId: string;
  env: WebEnvironment;
}>): Promise<DataQualityEnvelope> {
  const response = await getAnalyticsPayload(
    env,
    "analyticsDataQuality",
    accessToken,
    correlationId
  );
  return dataQualityEnvelopeSchema.parse(response);
}
