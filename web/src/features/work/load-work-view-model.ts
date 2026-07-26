import "server-only";

import { z } from "zod";

import type { WebEnvironment } from "@/server/config/environment";

import {
  getWorkActivity,
  getWorkActivityAssignments,
  getWorkActivityLogHistory,
  getWorkActivityLogs,
  getWorkActivityPage,
  type WorkActivity,
  type WorkActivityAssignment,
  type WorkActivityLog,
  type WorkActivityStatus,
  type WorkPage,
  type WorkReadContext
} from "./work-generated-client-adapter";
import {
  assertActivityScope,
  collectionState,
  selectionUnavailableState,
  unavailableState,
  type WorkCollectionState,
  type WorkHistoryState,
  type WorkViewModel
} from "./work-view-model-state";

export {
  hasWorkCollectionItems,
  type WorkCollectionState,
  type WorkHistoryState,
  type WorkSelectedActivity,
  type WorkSelectionState,
  type WorkUnavailableState,
  type WorkViewModel
} from "./work-view-model-state";

export type WorkViewModelFilters = Readonly<{
  status?: WorkActivityStatus;
  search?: string;
}>;

export type LoadWorkViewModelInput = Readonly<{
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
  filters?: WorkViewModelFilters;
  selectedActivityId?: string;
  selectedLogId?: string;
  logOffset?: number;
  historyOffset?: number;
}>;

const uuidSchema = z.uuid();

export async function loadWorkViewModel({
  env,
  accessToken,
  correlationId,
  filters = {},
  selectedActivityId,
  selectedLogId,
  logOffset = 0,
  historyOffset = 0
}: LoadWorkViewModelInput): Promise<WorkViewModel> {
  const context = { env, accessToken, correlationId };
  const normalizedFilters = {
    status: filters.status,
    search: filters.search?.trim() || undefined
  };

  let activityPage: WorkPage<WorkActivity>;
  try {
    activityPage = await getWorkActivityPage(context, normalizedFilters);
  } catch (error) {
    return {
      activities: unavailableState(error),
      selection: { status: "unselected" }
    };
  }

  const activities = collectionState(activityPage);
  if (!selectedActivityId) {
    return { activities, selection: { status: "unselected" } };
  }
  if (
    !uuidSchema.safeParse(selectedActivityId).success
    || !activityPage.items.some((activity) => activity.id === selectedActivityId)
  ) {
    return { activities, selection: { status: "not-found" } };
  }

  let activity: WorkActivity;
  try {
    activity = await getWorkActivity(context, selectedActivityId);
  } catch (error) {
    return { activities, selection: selectionUnavailableState(error) };
  }
  if (activity.id !== selectedActivityId) {
    return {
      activities,
      selection: { status: "failure", message: "Work data could not be loaded." }
    };
  }

  const [assignments, logs] = await Promise.all([
    loadActivityAssignments(context, selectedActivityId),
    loadActivityLogs(context, selectedActivityId, logOffset)
  ]);
  const history = await loadSelectedLogHistory(
    context,
    selectedActivityId,
    selectedLogId,
    historyOffset,
    logs
  );

  return {
    activities,
    selection: {
      status: "ready",
      data: {
        activity,
        assignments,
        logs,
        history,
        selectedLogId:
          history.status === "not-found" || history.status === "unselected"
            ? null
            : selectedLogId ?? null
      }
    }
  };
}

async function loadActivityAssignments(
  context: WorkReadContext,
  activityId: string
): Promise<WorkCollectionState<WorkActivityAssignment>> {
  try {
    const page = await getWorkActivityAssignments(context, activityId);
    assertActivityScope(page.items, activityId);
    return collectionState({
      ...page,
      items: page.items.filter((assignment) => assignment.active)
    });
  } catch (error) {
    return unavailableState(error);
  }
}

async function loadActivityLogs(
  context: WorkReadContext,
  activityId: string,
  offset: number
): Promise<WorkCollectionState<WorkActivityLog>> {
  try {
    const page = await getWorkActivityLogs(context, activityId, offset);
    assertActivityScope(page.items, activityId);
    return collectionState(page);
  } catch (error) {
    return unavailableState(error);
  }
}

async function loadSelectedLogHistory(
  context: WorkReadContext,
  activityId: string,
  selectedLogId: string | undefined,
  offset: number,
  logs: WorkCollectionState<WorkActivityLog>
): Promise<WorkHistoryState> {
  if (!selectedLogId) return { status: "unselected" };
  if (!uuidSchema.safeParse(selectedLogId).success) return { status: "not-found" };
  if (logs.status === "denied" || logs.status === "failure") return logs;
  if (
    logs.status === "empty"
    || !logs.items.some((log) => log.id === selectedLogId)
  ) {
    return { status: "not-found" };
  }
  try {
    const page = await getWorkActivityLogHistory(
      context,
      activityId,
      selectedLogId,
      offset
    );
    assertActivityScope(page.items, activityId);
    return collectionState(page);
  } catch (error) {
    return unavailableState(error);
  }
}
