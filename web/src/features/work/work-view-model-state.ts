import "server-only";

import {
  WorkReadError,
  type WorkActivity,
  type WorkActivityAssignment,
  type WorkActivityLog,
  type WorkPage
} from "./work-generated-client-adapter";

export type WorkUnavailableState =
  | Readonly<{
      status: "denied";
      reason: "unauthenticated" | "forbidden";
      message: string;
    }>
  | Readonly<{ status: "failure"; message: string }>;

type CollectionMetadata = Readonly<{
  hasMore: boolean;
  limit: number;
  offset: number;
}>;

export type WorkCollectionState<Item> =
  | (Readonly<{ status: "ready"; items: readonly Item[] }> & CollectionMetadata)
  | (Readonly<{ status: "empty"; items: readonly [] }> & CollectionMetadata)
  | WorkUnavailableState;

export type WorkHistoryState =
  | WorkCollectionState<WorkActivityLog>
  | Readonly<{ status: "unselected" }>
  | Readonly<{ status: "not-found" }>;

export type WorkSelectedActivity = Readonly<{
  activity: WorkActivity;
  assignments: WorkCollectionState<WorkActivityAssignment>;
  logs: WorkCollectionState<WorkActivityLog>;
  history: WorkHistoryState;
  selectedLogId: string | null;
}>;

export type WorkSelectionState =
  | Readonly<{ status: "unselected" }>
  | Readonly<{ status: "not-found" }>
  | Readonly<{ status: "ready"; data: WorkSelectedActivity }>
  | WorkUnavailableState;

export type WorkViewModel = Readonly<{
  activities: WorkCollectionState<WorkActivity>;
  selection: WorkSelectionState;
}>;

export function hasWorkCollectionItems<Item>(
  state: WorkCollectionState<Item>
): state is Extract<WorkCollectionState<Item>, { status: "ready" }> {
  return state.status === "ready";
}

export function collectionState<Item>(
  page: WorkPage<Item>
): WorkCollectionState<Item> {
  const metadata = {
    hasMore: page.hasMore,
    limit: page.limit,
    offset: page.offset
  };
  return page.items.length === 0
    ? { status: "empty", items: [], ...metadata }
    : { status: "ready", items: page.items, ...metadata };
}

export function assertActivityScope(
  items: readonly Readonly<{ activityId: string }>[],
  activityId: string
): void {
  if (items.some((item) => item.activityId !== activityId)) {
    throw new WorkReadError("failure", 502);
  }
}

export function unavailableState(error: unknown): WorkUnavailableState {
  if (error instanceof WorkReadError) {
    if (error.kind === "unauthenticated") {
      return {
        status: "denied",
        reason: "unauthenticated",
        message: error.message
      };
    }
    if (error.kind === "denied") {
      return { status: "denied", reason: "forbidden", message: error.message };
    }
    return { status: "failure", message: error.message };
  }
  return { status: "failure", message: "Work data could not be loaded." };
}

export function selectionUnavailableState(
  error: unknown
): WorkSelectionState {
  if (error instanceof WorkReadError && error.kind === "not_found") {
    return { status: "not-found" };
  }
  return unavailableState(error);
}

