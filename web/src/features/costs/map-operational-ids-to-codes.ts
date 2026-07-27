import "server-only";

import { z } from "zod";

import { executeAllowedOperation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

const canonicalUuid = z.string().regex(
  /^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/
);
const masterSchema = z.object({
  active: z.boolean(),
  code: z.string().trim().min(1).max(64),
  id: canonicalUuid
}).passthrough();

export type CostReadContext = Readonly<{
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
}>;

export class CostScopeResolutionError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "CostScopeResolutionError";
  }
}

export async function mapFarmIdToCode(
  context: CostReadContext,
  farmId: string
): Promise<string> {
  try {
    const response = await executeAllowedOperation(
      context.env,
      "farmById",
      context.accessToken,
      context.correlationId,
      {},
      { id: farmId }
    );
    if (response.status === 403 || response.status === 404) {
      throw new CostScopeResolutionError(
        "Nông trại được chọn không thuộc phạm vi chi phí hiện hành."
      );
    }
    if (!response.ok) {
      throw new Error(`Operational farm request failed with status ${response.status}`);
    }
    const farm = masterSchema.parse(await response.json());
    if (!farm.active) {
      throw new CostScopeResolutionError(
        "Nông trại đã ngừng hoạt động và không thể lọc chi phí mua hàng."
      );
    }
    return farm.code;
  } catch (error) {
    if (error instanceof CostScopeResolutionError) throw error;
    throw new CostScopeResolutionError(
      "Không thể xác minh mã nông trại trước khi gọi procurement analytics."
    );
  }
}
