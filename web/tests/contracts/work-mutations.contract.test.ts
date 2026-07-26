import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { correctWorkLog } from "@/features/work/correct-work-log";
import { readBoundedJson } from "@/features/work/work-api-security";
import { submitWorkLog } from "@/features/work/submit-work-log";
import { executeAllowedMutation } from "@/server/bff/upstream-client";

vi.mock("@/server/bff/upstream-client", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/server/bff/upstream-client")>();
  return { ...original, executeAllowedMutation: vi.fn() };
});

const activityId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
const employeeId = "4fc03f21-71ee-46dc-a271-8d67ad4369c5";
const logId = "5ad14032-82ff-47ed-b382-9e78be547ad6";
const context = {
  env: {} as never,
  accessToken: "server-token",
  correlationId: "correlation-mutation",
  idempotencyKey: "stable-retry-key"
};

describe("work mutation contracts", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedMutation).mockReset();
  });

  it("appends with an unchanged idempotency key and fixed audit reason", async () => {
    vi.mocked(executeAllowedMutation).mockResolvedValueOnce(
      Response.json({ id: logId }, { status: 201 })
    );

    await submitWorkLog(context, {
      activityId,
      employeeId,
      occurredAt: "2026-07-26T01:00:00Z",
      notes: "Đã tưới đủ",
      quantity: 500,
      unit: "LITRE"
    });

    expect(executeAllowedMutation).toHaveBeenCalledWith(
      context.env,
      "activityLogAppend",
      "server-token",
      "correlation-mutation",
      "stable-retry-key",
      {
        employeeId,
        occurredAt: "2026-07-26T01:00:00Z",
        notes: "Đã tưới đủ",
        quantity: 500,
        unit: "LITRE",
        reasonCode: "FIELD_LOG_APPEND"
      },
      { id: activityId }
    );
  });

  it("uses append-only correction without an If-Match input", async () => {
    vi.mocked(executeAllowedMutation).mockResolvedValueOnce(
      Response.json({ id: logId }, { status: 201 })
    );

    await correctWorkLog(context, {
      activityId,
      logId,
      correctionKind: "REPLACE",
      correctionReason: "Đơn vị ghi nhận ban đầu chưa đúng",
      occurredAt: "2026-07-26T01:05:00Z",
      notes: "Đã đối chiếu lại",
      quantity: 0.5,
      unit: "TONNE"
    });

    expect(executeAllowedMutation).toHaveBeenCalledWith(
      context.env,
      "activityLogCorrection",
      "server-token",
      "correlation-mutation",
      "stable-retry-key",
      expect.objectContaining({
        correctionKind: "REPLACE",
        correctionReason: "Đơn vị ghi nhận ban đầu chưa đúng",
        reasonCode: "FIELD_LOG_CORRECTION"
      }),
      { id: activityId, logId }
    );
    expect(
      vi.mocked(executeAllowedMutation).mock.calls[0]
    ).not.toContain("If-Match");
  });

  it("rejects an unpaired quantity before any upstream request", async () => {
    await expect(
      submitWorkLog(context, {
        activityId,
        employeeId,
        occurredAt: "2026-07-26T01:00:00Z",
        quantity: 10
      })
    ).rejects.toThrow("đơn vị");
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects VOID corrections that contain measured evidence", async () => {
    await expect(
      correctWorkLog(context, {
        activityId,
        logId,
        correctionKind: "VOID",
        correctionReason: "Bản ghi trùng",
        occurredAt: "2026-07-26T01:05:00Z",
        quantity: 10,
        unit: "KG"
      })
    ).rejects.toThrow("hủy hiệu lực");
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects JSON bodies above the BFF byte boundary", async () => {
    const request = new NextRequest(
      "https://app.example.test/api/work/activities/id/logs",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ notes: "x".repeat(64 * 1024) })
      }
    );

    await expect(readBoundedJson(request)).rejects.toMatchObject({
      code: "request_too_large",
      status: 413
    });
  });

  it("stops a streamed body as soon as it crosses the byte boundary", async () => {
    const encoder = new TextEncoder();
    let pullCount = 0;
    const body = new ReadableStream<Uint8Array>({
      pull(controller) {
        pullCount += 1;
        controller.enqueue(encoder.encode("x".repeat(33 * 1024)));
        if (pullCount === 2) controller.close();
      }
    });
    const request = new NextRequest(
      "https://app.example.test/api/work/activities/id/logs",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
        duplex: "half"
      }
    );

    await expect(readBoundedJson(request)).rejects.toMatchObject({
      code: "request_too_large",
      status: 413
    });
    expect(pullCount).toBe(2);
  });
});
