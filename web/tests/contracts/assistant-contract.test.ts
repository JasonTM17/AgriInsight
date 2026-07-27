import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { describe, expect, it } from "vitest";

import {
  assistantAnswerSchema,
  assistantQuerySchema
} from "@/features/assistant/assistant-contract";
import { AssistantResponsePanel } from "@/features/assistant/components/assistant-response-panel";
import { canAccessAssistant } from "@/lib/analytics-area-access";

const usage = {
  completionTokens: 20,
  promptCacheHitTokens: 40,
  promptCacheMissTokens: 60,
  promptTokens: 100,
  totalTokens: 120
};

describe("assistant contract and presentation", () => {
  it("accepts bounded plain-text queries and rejects caller provider controls", () => {
    expect(assistantQuerySchema.parse({ question: "Kho nào sắp thiếu vật tư?" }))
      .toMatchObject({ question: "Kho nào sắp thiếu vật tư?" });
    expect(() => assistantQuerySchema.parse({
      model: "caller-controlled",
      question: "Kho nào sắp thiếu?"
    })).toThrow();
    expect(() => assistantQuerySchema.parse({
      question: "<script>alert(1)</script>"
    })).toThrow();
  });

  it("requires an answered response to carry verified citations", () => {
    expect(() => assistantAnswerSchema.parse({
      answer: "Một kết luận không có nguồn.",
      citations: [],
      status: "answered",
      usage
    })).toThrow();
  });

  it("renders evidence as plain text with source and as-of metadata", () => {
    const answer = assistantAnswerSchema.parse({
      answer: "Kho WH-01 cần theo dõi [inventory:wh-01:mat-01].",
      citations: [{
        asOf: "2026-07-27",
        evidenceId: "inventory:wh-01:mat-01",
        excerpt: "Số ngày cung ứng thấp hơn ngưỡng vận hành.",
        sourceType: "inventory",
        title: "Tồn kho MAT-01 · WH-01"
      }],
      status: "answered",
      usage
    });
    const markup = renderToStaticMarkup(createElement(
      AssistantResponsePanel,
      { answer, error: null, loading: false, onRetry: () => undefined }
    ));

    expect(markup).toContain("Bằng chứng được trích dẫn");
    expect(markup).toContain("inventory:wh-01:mat-01");
    expect(markup).toContain("Dữ liệu đến 27/07/2026");
    expect(markup).not.toContain("<script");
  });

  it("matches backend role and permission eligibility", () => {
    expect(canAccessAssistant({
      permissions: new Set(["FARM_READ"]),
      roles: new Set(["FARM_MANAGER"])
    })).toBe(true);
    expect(canAccessAssistant({
      permissions: new Set(["INVENTORY_READ"]),
      roles: new Set(["INVENTORY_MANAGER"])
    })).toBe(true);
    expect(canAccessAssistant({
      permissions: new Set(["FARM_READ"]),
      roles: new Set(["SUPPLIER"])
    })).toBe(false);
  });
});
