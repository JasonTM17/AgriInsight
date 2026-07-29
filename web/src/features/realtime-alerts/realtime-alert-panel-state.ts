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
  | "session-expired"
  | "stale";

export type RealtimeAlertPanelFailure = Readonly<{
  code: string;
  correlationId?: string;
  title: string;
}>;

export type RealtimeAlertAcknowledgement =
  | Readonly<{ status: "idle" }>
  | Readonly<{
      idempotencyKey: string;
      observedAt: string;
      status: "acknowledging";
    }>
  | Readonly<{
      acknowledgedAt: string;
      observedAt: string;
      status: "acknowledged";
    }>
  | Readonly<{ observedAt: string; status: "acknowledgement-denied" }>
  | Readonly<{ observedAt: string; status: "alert-unavailable" }>
  | Readonly<{
      idempotencyKey: string;
      observedAt: string;
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

export const REALTIME_ALERT_STALE_AFTER_MS = 90_000;

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
    acknowledgements: reconcileAcknowledgements(
      state.acknowledgements,
      data.items
    ),
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

export function handleRealtimeAlertFeedFailure(
  state: RealtimeAlertPanelState,
  failure: RealtimeAlertPanelFailure,
  status: number
): RealtimeAlertPanelState {
  if (status === 401) return expireRealtimeAlertPanelSession(state);
  if (status === 403) return denyRealtimeAlertPanel(state);
  return failRealtimeAlertFeedLoad(state, failure);
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

export function expireRealtimeAlertPanelSession(
  state: RealtimeAlertPanelState
): RealtimeAlertPanelState {
  return {
    ...state,
    acknowledgements: {},
    failure: null,
    feed: null,
    status: "session-expired"
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
  const alert = state.feed?.data.items.find((item) => item.id === alertId);
  const current = getRealtimeAlertAcknowledgement(state, alertId);
  const currentForObservation = isAcknowledgementForObservation(
    current,
    alert?.lastObservedAt
  )
    ? current
    : IDLE_ACKNOWLEDGEMENT;
  if (
    !alert
    || alert.acknowledged
    || currentForObservation.status === "acknowledging"
    || currentForObservation.status === "acknowledgement-denied"
    || currentForObservation.status === "alert-unavailable"
  ) {
    return state;
  }
  const idempotencyKey = currentForObservation.status === "acknowledgement-unknown"
    ? currentForObservation.idempotencyKey
    : requestedKey;
  return withAcknowledgement(state, alertId, {
    idempotencyKey,
    observedAt: alert.lastObservedAt,
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
    observedAt: current.observedAt,
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
    || current.observedAt !== alert.lastObservedAt
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
    {
      acknowledgedAt: alert.acknowledgedAt,
      observedAt: alert.lastObservedAt,
      status: "acknowledged"
    }
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

export function denyRealtimeAlertAcknowledgement(
  state: RealtimeAlertPanelState,
  alertId: string
): RealtimeAlertPanelState {
  const observedAt = getAlertObservedAt(state, alertId);
  if (!observedAt) return state;
  return withAcknowledgement(state, alertId, {
    observedAt,
    status: "acknowledgement-denied"
  });
}

export function markRealtimeAlertUnavailable(
  state: RealtimeAlertPanelState,
  alertId: string
): RealtimeAlertPanelState {
  const observedAt = getAlertObservedAt(state, alertId);
  if (!observedAt) return state;
  return withAcknowledgement(state, alertId, {
    observedAt,
    status: "alert-unavailable"
  });
}

export function handleRealtimeAlertAcknowledgementFailure(
  state: RealtimeAlertPanelState,
  alertId: string,
  status: number
): RealtimeAlertPanelState {
  if (status === 401) return expireRealtimeAlertPanelSession(state);
  if (status === 403) {
    return denyRealtimeAlertAcknowledgement(state, alertId);
  }
  if (status === 404) return markRealtimeAlertUnavailable(state, alertId);
  return clearRealtimeAlertAcknowledgement(state, alertId);
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

export function isRealtimeAlertPanelTerminalStatus(
  status: RealtimeAlertPanelStatus
): boolean {
  return status === "denied" || status === "session-expired";
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

function getAlertObservedAt(
  state: RealtimeAlertPanelState,
  alertId: string
): string | undefined {
  return state.feed?.data.items.find((item) => item.id === alertId)?.lastObservedAt;
}

function isAcknowledgementForObservation(
  acknowledgement: RealtimeAlertAcknowledgement,
  observedAt: string | undefined
): boolean {
  return acknowledgement.status !== "idle"
    && observedAt !== undefined
    && acknowledgement.observedAt === observedAt;
}

function reconcileAcknowledgements(
  acknowledgements: Readonly<Record<string, RealtimeAlertAcknowledgement>>,
  alerts: readonly RealtimeOperationalAlert[]
): Readonly<Record<string, RealtimeAlertAcknowledgement>> {
  const next = { ...acknowledgements };
  for (const alert of alerts) {
    const current = next[alert.id];
    if (alert.acknowledged && alert.acknowledgedAt !== null) {
      next[alert.id] = {
        acknowledgedAt: alert.acknowledgedAt,
        observedAt: alert.lastObservedAt,
        status: "acknowledged"
      };
      continue;
    }
    if (
      current
      && current.status !== "idle"
      && current.observedAt !== alert.lastObservedAt
    ) {
      delete next[alert.id];
      continue;
    }
    if (
      current?.status === "acknowledged"
      && current.observedAt === alert.lastObservedAt
    ) {
      delete next[alert.id];
    }
  }
  return next;
}
