import { beforeEach, describe, expect, it, vi } from "vitest";

import { toInventoryMutationResponse } from "@/features/inventory/inventory-mutation-response";
import {
  postInventoryTransactionSchema,
  reversalInventoryTransactionSchema
} from "@/features/inventory/inventory-mutation-contract";
import { postInventoryReversal } from "@/features/inventory/post-inventory-reversal";
import { postInventoryTransaction } from "@/features/inventory/post-inventory-transaction";
import {
  executeAllowedMutation,
  executeAllowedOperation
} from "@/server/bff/upstream-client";

vi.mock("@/server/bff/upstream-client", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/server/bff/upstream-client")>();
  return {
    ...original,
    executeAllowedMutation: vi.fn(),
    executeAllowedOperation: vi.fn()
  };
});

const warehouseId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
const otherWarehouseId = "41d9e7a6-ad44-47a8-8c63-a7bac7d60e6a";
const materialId = "4fc03f21-71ee-46dc-a271-8d67ad4369c5";
const supplierId = "5ad14032-82ff-47ed-b382-9e78be547ad6";
const stockLotId = "66050634-6a22-45c6-a896-5a83602caf45";
const transactionId = "7935a09b-1f9f-4d08-a58a-a45bdd4e449d";

describe("inventory mutation contracts", () => {
  it("accepts an exact receipt shape", () => {
    expect(
      postInventoryTransactionSchema.parse({
        kind: "RECEIPT",
        warehouseId,
        materialId,
        supplierId,
        quantityBase: 25.5,
        unitCostVnd: 12_500,
        batchCode: " NPK-2027-01 ",
        expiryDate: "2027-12-31",
        occurredAt: "2027-01-01T08:00:00Z",
        referenceCode: " PO-42 "
      })
    ).toMatchObject({
      batchCode: "NPK-2027-01",
      kind: "RECEIPT",
      referenceCode: "PO-42"
    });
  });

  it.each([
    ["supplier", { supplierId: undefined }],
    ["unit cost", { unitCostVnd: undefined }],
    ["batch", { batchCode: undefined }],
    ["expiry", { expiryDate: undefined }],
    ["issue lot", { stockLotId }],
    ["issue reason", { reason: "Apply to field" }]
  ])("rejects a receipt with invalid %s semantics", (_case, override) => {
    expect(() =>
      postInventoryTransactionSchema.parse({
        kind: "RECEIPT",
        warehouseId,
        materialId,
        supplierId,
        quantityBase: 25.5,
        unitCostVnd: 12_500,
        batchCode: "NPK-2027-01",
        expiryDate: "2027-12-31",
        occurredAt: "2027-01-01T08:00:00Z",
        ...override
      })
    ).toThrow();
  });

  it("leaves expiry-versus-occurrence ordering to the upstream contract", () => {
    expect(() =>
      postInventoryTransactionSchema.parse({
        kind: "RECEIPT",
        warehouseId,
        materialId,
        supplierId,
        quantityBase: 25.5,
        unitCostVnd: 12_500,
        batchCode: "NPK-2027-01",
        expiryDate: "2026-12-31",
        occurredAt: "2027-01-01T08:00:00Z"
      })
    ).not.toThrow();
  });

  it("accepts an issue with an optional authoritative source lot", () => {
    expect(
      postInventoryTransactionSchema.parse({
        kind: "ISSUE",
        warehouseId,
        materialId,
        quantityBase: 2,
        stockLotId,
        occurredAt: "2027-01-02T08:00:00Z",
        reason: " Apply to field F-12 "
      })
    ).toMatchObject({
      kind: "ISSUE",
      reason: "Apply to field F-12",
      stockLotId
    });
  });

  it.each([
    ["missing reason", { reason: undefined }],
    ["supplier", { supplierId }],
    ["unit cost", { unitCostVnd: 100 }],
    ["batch", { batchCode: "BATCH" }],
    ["expiry", { expiryDate: "2027-12-31" }]
  ])("rejects an issue with invalid %s semantics", (_case, override) => {
    expect(() =>
      postInventoryTransactionSchema.parse({
        kind: "ISSUE",
        warehouseId,
        materialId,
        quantityBase: 2,
        occurredAt: "2027-01-02T08:00:00Z",
        reason: "Apply to field",
        ...override
      })
    ).toThrow();
  });

  it("accepts a bounded reversal and rejects unknown fields", () => {
    expect(
      reversalInventoryTransactionSchema.parse({
        transactionId,
        quantityBase: 1.25,
        reason: " Correct duplicate posting "
      })
    ).toEqual({
      transactionId,
      quantityBase: 1.25,
      reason: "Correct duplicate posting"
    });
    expect(() =>
      reversalInventoryTransactionSchema.parse({
        transactionId,
        quantityBase: 1.25,
        reason: "Correct duplicate posting",
        ifMatch: "\"7\""
      })
    ).toThrow();
  });

  it.each([0, -1, 0.00001, Number.NaN, Number.POSITIVE_INFINITY])(
    "rejects unsafe inventory quantity %s",
    (quantityBase) => {
      expect(() =>
        reversalInventoryTransactionSchema.parse({
          transactionId,
          quantityBase,
          reason: "Correction"
        })
      ).toThrow();
    }
  );
});

const mutationContext = {
  env: {} as never,
  accessToken: "server-token",
  correlationId: "correlation-mutation",
  idempotencyKey: "stable-retry-key"
};

const warehouse = {
  id: warehouseId,
  code: "WH-01",
  displayName: "Kho trung tâm",
  active: true,
  version: 1
} as const;

const receiptBody = {
  kind: "RECEIPT" as const,
  warehouseId,
  materialId,
  supplierId,
  quantityBase: 250,
  unitCostVnd: 12_500,
  batchCode: "BATCH-01",
  expiryDate: "2028-12-31",
  occurredAt: "2027-01-01T08:00:00Z",
  referenceCode: "PO-2027-00042"
};

const issueBody = {
  kind: "ISSUE" as const,
  warehouseId,
  materialId,
  quantityBase: 10,
  occurredAt: "2027-01-01T09:00:00Z",
  reason: "Applied to field F-12"
};

const transactionRecord = {
  id: transactionId,
  warehouseId,
  materialId,
  kind: "RECEIPT",
  unit: "KG",
  quantityBase: 250,
  signedQuantityEffect: 250,
  procurementEffectVnd: 3_125_000,
  occurredAt: "2027-01-01T08:00:00Z",
  recordedByProfileId: "a4b5d235-1d78-49ea-924f-a2f865c73238",
  version: 7
} as const;

function warehousePage(items: readonly unknown[]) {
  return { items, limit: 100, offset: 0, hasMore: false };
}

function transactionDetailResponse(etag: string) {
  return new Response(JSON.stringify(transactionRecord), {
    status: 200,
    headers: { "Content-Type": "application/json", ETag: etag }
  });
}

describe("post inventory transaction", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedOperation).mockReset();
    vi.mocked(executeAllowedMutation).mockReset();
  });

  it("posts a receipt with the fixed audit reason code and no If-Match", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json(warehousePage([warehouse]))
    );
    vi.mocked(executeAllowedMutation).mockResolvedValueOnce(
      Response.json({ id: transactionId }, { status: 201 })
    );

    await postInventoryTransaction(mutationContext, receiptBody);

    expect(executeAllowedMutation).toHaveBeenCalledWith(
      mutationContext.env,
      "inventoryTransactionPost",
      mutationContext.accessToken,
      mutationContext.correlationId,
      mutationContext.idempotencyKey,
      { ...receiptBody, reasonCode: "WEB_RECEIPT_ENTRY" },
      {}
    );
    expect(vi.mocked(executeAllowedMutation).mock.calls[0]).toHaveLength(7);
  });

  it("posts an issue with the fixed audit reason code and no If-Match", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json(warehousePage([warehouse]))
    );
    vi.mocked(executeAllowedMutation).mockResolvedValueOnce(
      Response.json({ id: transactionId }, { status: 201 })
    );

    await postInventoryTransaction(mutationContext, issueBody);

    expect(executeAllowedMutation).toHaveBeenCalledWith(
      mutationContext.env,
      "inventoryTransactionPost",
      mutationContext.accessToken,
      mutationContext.correlationId,
      mutationContext.idempotencyKey,
      { ...issueBody, reasonCode: "WEB_ISSUE_ENTRY" },
      {}
    );
    expect(vi.mocked(executeAllowedMutation).mock.calls[0]).toHaveLength(7);
  });

  it("rejects a warehouse outside the server-visible set before mutating", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json(warehousePage([{ ...warehouse, id: otherWarehouseId }]))
    );

    await expect(
      postInventoryTransaction(mutationContext, receiptBody)
    ).rejects.toMatchObject({ status: 403 });
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects a malformed receipt before any upstream call", async () => {
    const malformedReceipt: Record<string, unknown> = { ...receiptBody };
    delete malformedReceipt.supplierId;

    await expect(
      postInventoryTransaction(mutationContext, malformedReceipt)
    ).rejects.toThrow();
    expect(executeAllowedOperation).not.toHaveBeenCalled();
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects an issue payload carrying receipt-only finance fields", async () => {
    const invalidIssue = { ...issueBody, supplierId };

    await expect(
      postInventoryTransaction(mutationContext, invalidIssue)
    ).rejects.toThrow();
    expect(executeAllowedOperation).not.toHaveBeenCalled();
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });
});

describe("post inventory reversal", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedOperation).mockReset();
    vi.mocked(executeAllowedMutation).mockReset();
  });

  it("reverses after confirming the caller's If-Match matches the current version", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      transactionDetailResponse("\"7\"")
    );
    vi.mocked(executeAllowedMutation).mockResolvedValueOnce(
      Response.json({ id: transactionId }, { status: 201 })
    );

    await postInventoryReversal(
      mutationContext,
      { transactionId, quantityBase: 25, reason: "Correct duplicate warehouse posting" },
      "\"7\""
    );

    expect(executeAllowedMutation).toHaveBeenCalledWith(
      mutationContext.env,
      "inventoryTransactionReversal",
      mutationContext.accessToken,
      mutationContext.correlationId,
      mutationContext.idempotencyKey,
      {
        quantityBase: 25,
        reason: "Correct duplicate warehouse posting",
        reasonCode: "WEB_REVERSAL_ENTRY"
      },
      { id: transactionId },
      "\"7\""
    );
  });

  it("rejects a stale If-Match before attempting the reversal", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      transactionDetailResponse("\"8\"")
    );

    await expect(
      postInventoryReversal(
        mutationContext,
        { transactionId, quantityBase: 25, reason: "Correct duplicate warehouse posting" },
        "\"7\""
      )
    ).rejects.toMatchObject({ status: 409 });
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });

  it("rejects a malformed reversal payload before any upstream call", async () => {
    await expect(
      postInventoryReversal(
        mutationContext,
        { transactionId, quantityBase: -1, reason: "x" },
        "\"7\""
      )
    ).rejects.toThrow();
    expect(executeAllowedOperation).not.toHaveBeenCalled();
    expect(executeAllowedMutation).not.toHaveBeenCalled();
  });
});

describe("inventory mutation response normalizer", () => {
  it.each([
    [403, "scope_denied", "Bạn không còn quyền ghi trong phạm vi kho này."],
    [404, "inventory_not_found", "Giao dịch hoặc kho không còn trong phạm vi."],
    [409, "inventory_conflict", "Máy chủ đã có trạng thái khác. Hãy tải lại trước khi gửi."]
  ])(
    "sanitizes an upstream %s response without relaying its body",
    async (status, code, message) => {
      const upstream = new Response(
        JSON.stringify({ secret: "internal-detail", traceId: "abc" }),
        { status, headers: { "Content-Type": "application/json" } }
      );

      const response = await toInventoryMutationResponse(
        upstream,
        "correlation-response"
      );

      expect(response.status).toBe(status);
      const body = await response.json();
      expect(body).toEqual({
        type: "about:blank",
        title: message,
        status,
        code,
        correlationId: "correlation-response"
      });
      expect(JSON.stringify(body)).not.toContain("internal-detail");
    }
  );

  it("maps an invalid upstream success body to a sanitized 502", async () => {
    const upstream = Response.json(
      { not: "a transaction" },
      { status: 201, headers: { ETag: "\"1\"" } }
    );

    const response = await toInventoryMutationResponse(
      upstream,
      "correlation-invalid"
    );

    expect(response.status).toBe(502);
    const body = await response.json();
    expect(body.code).toBe("invalid_upstream_response");
  });

  it("maps a missing ETag on an otherwise valid body to a sanitized 502", async () => {
    const upstream = Response.json(transactionRecord, { status: 201 });

    const response = await toInventoryMutationResponse(
      upstream,
      "correlation-missing-etag"
    );

    expect(response.status).toBe(502);
    const body = await response.json();
    expect(body.code).toBe("invalid_upstream_response");
  });

  it("passes through a valid transaction with its ETag preserved", async () => {
    const upstream = Response.json(transactionRecord, {
      status: 201,
      headers: { ETag: "\"7\"" }
    });

    const response = await toInventoryMutationResponse(upstream, "correlation-ok");

    expect(response.status).toBe(201);
    expect(response.headers.get("ETag")).toBe("\"7\"");
    const body = await response.json();
    expect(body.id).toBe(transactionId);
  });
});
