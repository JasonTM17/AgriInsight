import { describe, expectTypeOf, it } from "vitest";

import type {
  AnalyticsQuery,
  AnalyticsResponse
} from "@/server/clients/analytics";
import type { components as AnalyticsComponents } from "@/server/generated/analytics/schema";
import type { components as BackendComponents } from "@/server/generated/backend/schema";
import type { CurrentUserResponse } from "@/server/clients/backend";

describe("generated client contract types", () => {
  it("binds backend and analytics client output to generated schemas", () => {
    expectTypeOf<CurrentUserResponse>().toEqualTypeOf<
      BackendComponents["schemas"]["CurrentUserResponse"]
    >();
    expectTypeOf<AnalyticsResponse<"analyticsOverview">>().toEqualTypeOf<
      AnalyticsComponents["schemas"]["AnalyticsEnvelope_OverviewPayload_"]
    >();
    expectTypeOf<
      NonNullable<AnalyticsQuery<"analyticsFarms">>
    >().toEqualTypeOf<{
      readonly crop_code?: string | null;
      readonly date_preset?: "all" | "last-30-days" | "season-to-date";
      readonly farm_code?: string | null;
      readonly field_code?: string | null;
      readonly limit?: number;
      readonly offset?: number;
      readonly season_code?: string | null;
      readonly sort?: "farm_code" | "profit_desc";
    }>();
  });
});
