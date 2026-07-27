import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { describe, expect, it } from "vitest";

import { CropHealthFieldDetail } from "@/features/crop-quality/components/crop-health-field-detail";
import { CropHealthPage } from "@/features/crop-quality/components/crop-health-page";
import { DataQualityPage } from "@/features/crop-quality/components/data-quality-page";
import {
  cropHealthEnvelopeSchema,
  dataQualityEnvelopeSchema
} from "@/features/crop-quality/analytics-evidence-contract";
import { parseCropHealthRouteState } from "@/features/crop-quality/crop-health-route-state";

import {
  cropHealthEnvelopeFixture,
  dataQualityEnvelopeFixture
} from "../support/crop-quality-fixtures";

describe("crop health and data quality views", () => {
  it("renders the permanent crop demo warning and evidence taxonomy", () => {
    const envelope = cropHealthEnvelopeSchema.parse(cropHealthEnvelopeFixture);
    const state = parseCropHealthRouteState({})!;
    const markup = renderToStaticMarkup(createElement(CropHealthPage, {
      correlationId: "correlation-crop",
      envelope,
      state
    }));

    expect(markup).toContain("Ảnh minh họa do AI tạo — chỉ dùng cho demo; không phải bằng chứng thực địa.");
    expect(markup).toContain("dataStatus=current");
    expect(markup).toContain("assessmentMethod=rule-based-heuristic");
    expect(markup).toContain("severity=low");
    expect(markup).toContain("/crop-health/FIELD-001");
    expect(markup).not.toContain("riskScore");
  });

  it("renders a scoped field detail with the same demo boundary", () => {
    const envelope = cropHealthEnvelopeSchema.parse(cropHealthEnvelopeFixture);
    const markup = renderToStaticMarkup(createElement(CropHealthFieldDetail, {
      correlationId: "correlation-field",
      envelope,
      fieldCode: "FIELD-001"
    }));

    expect(markup).toContain('data-testid="crop-health-detail-page"');
    expect(markup).toContain("Khu Bắc");
    expect(markup).toContain("Ảnh minh họa do AI tạo — chỉ dùng cho demo; không phải bằng chứng thực địa.");
  });

  it("renders data quality remediation and contract metadata without speculative language", () => {
    const envelope = dataQualityEnvelopeSchema.parse(dataQualityEnvelopeFixture);
    const markup = renderToStaticMarkup(createElement(DataQualityPage, {
      correlationId: "correlation-quality",
      envelope
    }));

    expect(markup).toContain('data-testid="data-quality-page"');
    expect(markup).toContain("assessmentMethod=rule-based-heuristic");
    expect(markup).toContain("Dòng cách ly");
  });
});
