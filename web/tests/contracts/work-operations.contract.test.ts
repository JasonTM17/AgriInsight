import { beforeEach, describe, expect, it, vi } from "vitest";

import { loadWorkViewModel } from "@/features/work/load-work-view-model";
import {
  parseWorkRouteState,
  workHref
} from "@/features/work/work-route-state";
import { executeAllowedOperation } from "@/server/bff/upstream-client";

vi.mock("@/server/bff/upstream-client", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/server/bff/upstream-client")>();
  return { ...original, executeAllowedOperation: vi.fn() };
});

const activityId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
const assignmentId = "41d9e7a6-ad44-47a8-8c63-a7bac7d60e6a";
const employeeId = "4fc03f21-71ee-46dc-a271-8d67ad4369c5";
const logId = "5ad14032-82ff-47ed-b382-9e78be547ad6";

const activity = {
  id: activityId,
  farmId: "66050634-6a22-45c6-a896-5a83602caf45",
  fieldId: "7935a09b-1f9f-4d08-a58a-a45bdd4e449d",
  seasonId: "80095c25-b595-4691-b98b-ce84fa3e2bfd",
  code: "ACT-IRRIGATION-001",
  title: "Tưới khu vực 1",
  description: "Tưới theo lịch vận hành",
  activityType: "IRRIGATION",
  status: "STARTED",
  plannedStartAt: "2026-07-26T00:00:00Z",
  dueAt: "2026-07-26T04:00:00Z",
  startedAt: "2026-07-26T00:10:00Z",
  completedAt: null,
  cancelledAt: null,
  version: 1
} as const;

const assignment = {
  id: assignmentId,
  activityId,
  employeeId,
  active: true,
  version: 0
} as const;

const log = {
  id: logId,
  activityId,
  employeeId,
  authorProfileId: "9b2bd575-194d-42c9-9f72-2038ad623c7a",
  occurredAt: "2026-07-26T01:00:00Z",
  notes: "Đã tưới đủ theo lịch",
  quantity: 500,
  unit: "LITRE",
  evidenceUri: null,
  correctsLogId: null,
  correctionKind: null,
  correctionReason: null,
  version: 0
} as const;

describe("work generated-client loader", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedOperation).mockReset();
    vi.mocked(executeAllowedOperation).mockImplementation(
      async (_env, operation) => {
        if (operation === "activityCatalog") {
          return Response.json(page([activity], 25));
        }
        if (operation === "activityById") return Response.json(activity);
        if (operation === "activityAssignments") {
          return Response.json(page([assignment], 50));
        }
        if (operation === "activityLogs") {
          return Response.json(page([log], 50));
        }
        if (operation === "activityLogHistory") {
          return Response.json(page([log], 50));
        }
        throw new Error(`Unexpected operation: ${operation}`);
      }
    );
  });

  it("uses only frozen activity GET operations for a selected work item", async () => {
    const result = await loadWorkViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-work",
      filters: { search: "  tưới ", status: "STARTED" },
      selectedActivityId: activityId,
      selectedLogId: logId
    });

    expect(result.activities.status).toBe("ready");
    expect(result.selection.status).toBe("ready");
    if (result.selection.status !== "ready") {
      throw new Error("Expected selected activity");
    }
    expect(result.selection.data.history.status).toBe("ready");
    expect(vi.mocked(executeAllowedOperation).mock.calls.map(
      ([, operation]) => operation
    )).toEqual([
      "activityCatalog",
      "activityById",
      "activityAssignments",
      "activityLogs",
      "activityLogHistory"
    ]);
    expect(vi.mocked(executeAllowedOperation)).toHaveBeenNthCalledWith(
      1,
      expect.anything(),
      "activityCatalog",
      "server-token",
      "correlation-work",
      { limit: 25, offset: 0, search: "tưới", status: "STARTED" },
      {}
    );
  });

  it("does not issue per-activity child requests before selection", async () => {
    const result = await loadWorkViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-queue"
    });

    expect(result.selection).toEqual({ status: "unselected" });
    expect(executeAllowedOperation).toHaveBeenCalledTimes(1);
  });

  it("rejects a selected UUID outside the scoped first page", async () => {
    const result = await loadWorkViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-scope",
      selectedActivityId: "a4b5d235-1d78-49ea-924f-a2f865c73238"
    });

    expect(result.selection).toEqual({ status: "not-found" });
    expect(executeAllowedOperation).toHaveBeenCalledTimes(1);
  });

  it("maps a catalog 403 to a generic denied collection", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      new Response(null, { status: 403 })
    );

    const result = await loadWorkViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-denied"
    });

    expect(result.activities).toMatchObject({
      status: "denied",
      reason: "forbidden"
    });
    expect(result.selection).toEqual({ status: "unselected" });
  });

  it("fails closed when a child response escapes the selected activity", async () => {
    vi.mocked(executeAllowedOperation).mockImplementation(
      async (_env, operation) => {
        if (operation === "activityCatalog") {
          return Response.json(page([activity], 25));
        }
        if (operation === "activityById") return Response.json(activity);
        if (operation === "activityAssignments") {
          return Response.json(
            page([
              {
                ...assignment,
                activityId: "a4b5d235-1d78-49ea-924f-a2f865c73238"
              }
            ], 50)
          );
        }
        return Response.json(page([], 50));
      }
    );

    const result = await loadWorkViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-boundary",
      selectedActivityId: activityId
    });

    if (result.selection.status !== "ready") {
      throw new Error("Expected selected activity");
    }
    expect(result.selection.data.assignments.status).toBe("failure");
  });

  it("requests exact bounded offsets for logs and immutable history", async () => {
    vi.mocked(executeAllowedOperation).mockImplementation(
      async (_env, operation, _token, _correlation, query) => {
        if (operation === "activityCatalog") {
          return Response.json(page([activity], 25));
        }
        if (operation === "activityById") return Response.json(activity);
        if (operation === "activityAssignments") {
          return Response.json(page([assignment], 50));
        }
        if (operation === "activityLogs") {
          return Response.json(page([log], 50, Number(query?.offset), true));
        }
        if (operation === "activityLogHistory") {
          return Response.json(page([log], 50, Number(query?.offset), true));
        }
        throw new Error(`Unexpected operation: ${operation}`);
      }
    );

    const result = await loadWorkViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-pages",
      selectedActivityId: activityId,
      selectedLogId: logId,
      logOffset: 50,
      historyOffset: 100
    });

    if (result.selection.status !== "ready") {
      throw new Error("Expected paginated selected activity");
    }
    expect(result.selection.data.logs).toMatchObject({
      hasMore: true,
      limit: 50,
      offset: 50,
      status: "ready"
    });
    expect(result.selection.data.history).toMatchObject({
      hasMore: true,
      limit: 50,
      offset: 100,
      status: "ready"
    });
    expect(executeAllowedOperation).toHaveBeenNthCalledWith(
      4,
      expect.anything(),
      "activityLogs",
      "server-token",
      "correlation-pages",
      { limit: 50, offset: 50 },
      { id: activityId }
    );
    expect(executeAllowedOperation).toHaveBeenNthCalledWith(
      5,
      expect.anything(),
      "activityLogHistory",
      "server-token",
      "correlation-pages",
      { limit: 50, offset: 100 },
      { id: activityId, logId }
    );
  });

  it("accepts only bounded 50-row route offsets", () => {
    const state = parseWorkRouteState({
      activityId,
      historyOffset: "100",
      logId,
      logOffset: "50"
    });

    expect(state).not.toBeNull();
    expect(state && workHref(state, {
      activityId,
      historyOffset: state.historyOffset,
      logId,
      logOffset: state.logOffset
    })).toBe(
      `/work?activityId=${activityId}&logOffset=50&logId=${logId}`
      + "&historyOffset=100"
    );
    expect(parseWorkRouteState({ activityId, logOffset: "49" })).toBeNull();
    expect(
      parseWorkRouteState({ activityId, logOffset: "10050" })
    ).toBeNull();
    expect(parseWorkRouteState({ historyOffset: "50" })).toBeNull();
  });
});

function page<Item>(
  items: readonly Item[],
  limit: 25 | 50,
  offset = 0,
  hasMore = false
) {
  return { items, limit, offset, hasMore };
}
