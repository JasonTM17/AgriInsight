import { afterEach, describe, expect, it, vi } from "vitest";

import { queryAssistant } from "@/features/assistant/assistant-api-client";

describe("assistant API client", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("propagates cancellation to a request that is still pending", async () => {
    vi.stubGlobal("document", {
      cookie: "__Host-agriinsight-csrf=test-csrf"
    });
    const fetchStub = vi.fn(
      (_input: RequestInfo | URL, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener(
            "abort",
            () => reject(new DOMException("Request aborted.", "AbortError")),
            { once: true }
          );
        })
    );
    vi.stubGlobal("fetch", fetchStub);
    const controller = new AbortController();

    const pending = queryAssistant(
      { question: "Kho nào sắp thiếu vật tư?" },
      controller.signal
    );
    controller.abort();

    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
    expect(fetchStub).toHaveBeenCalledWith(
      "/api/assistant/query",
      expect.objectContaining({ signal: controller.signal })
    );
  });
});
