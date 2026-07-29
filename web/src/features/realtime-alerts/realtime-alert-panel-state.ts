import type {
  RealtimeOperationalAlert,
  RealtimeOperationalAlertFeed
} from "./realtime-alert-contract";

export type RealtimeAlertPanelStatus =
  | "closed"
  | "denied"
  | "failed"
  | "loading"
  | "partial"
  | "ready"
  | "stale";

export type RealtimeAlertPanelFailure = Readonly<{
  code: string;
  correlationId?: string;
  title: string;
}>;

export type RealtimeAlertAcknowledgement =
  | Readonly<{ status: "idle" }>
  | Readonly<{ idempotencyKey: string; status: "acknowledging" }>
  | Readonly<{ acknowledgedAt: string; status: "acknowledged" }>
  | Readonly<{
      idempotencyKey: string;
      status: "acknowledgement-unknown";
    }>;

export type RealtimeAlertFeedWindow = Readonly<{
  data: RealtimeOperationalAlertFeed;
  isPartial: boolean;
  isStale: boolean;
}>;

export type RealtimeAlertPanelState = Readonly<{
  acknowledgements: Readonly<Record<string, RealtimeAlertAcknowledgement>>;
  failure: RealtimeAlertPanelFailure | null;
  feed: RealtimeAlertFeedWindow | null;
  status: RealtimeAlertPanelStatus;
}>;

const IDLE_ACKNOWLEDGEMENT: RealtimeAlertAcknowledgement = { status: "idle" };

export function createRealtimeAlertPanelState(): RealtimeAlertPanelState {
  return {
    acknowledgements: {},
    failure: null,
    feed: null,
    status: "closed"
  };
}

export function beginRealtimeAlertFeedLoad(
  state: RealtimeAlertPanelState
): RealtimeAlertPanelState {
  return { ...state, failure: null, status: "loading" };
}

export function receiveRealtimeAlertFeed(
  state: RealtimeAlertPanelState,
  data: RealtimeOperationalAlertFeed,
  nowMs: number,
  staleAfterMs: number
): RealtimeAlertPanelState {
  const feed: RealtimeAlertFeedWindow = {
    data,
    isPartial: data.hasMore,
    isStale: isRealtimeAlertFeedStale(data, nowMs, staleAfterMs)
  };
  return {
    ...state,
    failure: null,
    feed,
    status: feed.isPartial ? "partial" : feed.isStale ? "stale" : "ready"
  };
}

export function failRealtimeAlertFeedLoad(
  state: RealtimeAlertPanelState,
  failure: RealtimeAlertPanelFailure
): RealtimeAlertPanelState {
  return { ...state, failure, status: "failed" };
}

export function denyRealtimeAlertPanel(
  state: RealtimeAlertPanelState
): RealtimeAlertPanelState {
  return {
    ...state,
    acknowledgements: {},
    failure: null,
    feed: null,
    status: "denied"
  };
}

export function closeRealtimeAlertPanel(
  state: RealtimeAlertPanelState
): RealtimeAlertPanelState {
  return { ...state, failure: null, feed: null, status: "closed" };
}

export function beginRealtimeAlertAcknowledgement(
  state: RealtimeAlertPanelState,
  alertId: string,
  requestedKey: string
): RealtimeAlertPanelState {
  const current = getRealtimeAlertAcknowledgement(state, alertId);
  const alert = state.feed?.data.items.find((item) => item.id === alertId);
  if (!alert || alert.acknowledged || current.status === "acknowledging") {
    return state;
  }
  const idempotencyKey = current.status === "acknowledgement-unknown"
    ? current.idempotencyKey
    : requestedKey;
  return withAcknowledgement(state, alertId, {
    idempotencyKey,
    status: "acknowledging"
  });
}

export function markRealtimeAlertAcknowledgementUnknown(
  state: RealtimeAlertPanelState,
  alertId: string
): RealtimeAlertPanelState {
  const current = getRealtimeAlertAcknowledgement(state, alertId);
  if (current.status !== "acknowledging") return state;
  return withAcknowledgement(state, alertId, {
    idempotencyKey: current.idempotencyKey,
    status: "acknowledgement-unknown"
  });
}

export function completeRealtimeAlertAcknowledgement(
  state: RealtimeAlertPanelState,
  alertId: string,
  alert: RealtimeOperationalAlert
): RealtimeAlertPanelState {
  const current = getRealtimeAlertAcknowledgement(state, alertId);
  if (
    current.status !== "acknowledging"
    || alert.id !== alertId
    || !alert.acknowledged
    || alert.acknowledgedAt === null
  ) {
    return markRealtimeAlertAcknowledgementUnknown(state, alertId);
  }
  const feed = state.feed && {
    ...state.feed,
    data: {
      ...state.feed.data,
      items: state.feed.data.items.map((item) => item.id === alertId ? alert : item)
    }
  };
  return withAcknowledgement(
    { ...state, feed },
    alertId,
    { acknowledgedAt: alert.acknowledgedAt, status: "acknowledged" }
  );
}

export function clearRealtimeAlertAcknowledgement(
  state: RealtimeAlertPanelState,
  alertId: string
): RealtimeAlertPanelState {
  if (!(alertId in state.acknowledgements)) return state;
  const acknowledgements = Object.fromEntries(
    Object.entries(state.acknowledgements).filter(([id]) => id !== alertId)
  );
  return { ...state, acknowledgements };
}

export function getRealtimeAlertAcknowledgement(
  state: RealtimeAlertPanelState,
  alertId: string
): RealtimeAlertAcknowledgement {
  return state.acknowledgements[alertId] ?? IDLE_ACKNOWLEDGEMENT;
}

export function getRealtimeAlertAcknowledgementKey(
  state: RealtimeAlertPanelState,
  alertId: string
): string | undefined {
  const acknowledgement = getRealtimeAlertAcknowledgement(state, alertId);
  return acknowledgement.status === "acknowledging"
    || acknowledgement.status === "acknowledgement-unknown"
    ? acknowledgement.idempotencyKey
    : undefined;
}

export function isRealtimeAlertFeedStale(
  data: RealtimeOperationalAlertFeed,
  nowMs: number,
  staleAfterMs: number
): boolean {
  const generatedAtMs = Date.parse(data.generatedAt);
  if (
    !Number.isFinite(generatedAtMs)
    || !Number.isFinite(nowMs)
    || !Number.isFinite(staleAfterMs)
    || staleAfterMs < 0
  ) return true;
  return Math.max(0, nowMs - generatedAtMs) > staleAfterMs;
}

export function isRealtimeAlertPollingEligible(
  isOpen: boolean,
  documentVisibility: string | undefined,
  requestInFlight: boolean = false
): boolean {
  return isOpen && !requestInFlight && documentVisibility === "visible";
}

function withAcknowledgement(
  state: RealtimeAlertPanelState,
  alertId: string,
  acknowledgement: RealtimeAlertAcknowledgement
): RealtimeAlertPanelState {
  return {
    ...state,
    acknowledgements: { ...state.acknowledgements, [alertId]: acknowledgement }
  };
}
