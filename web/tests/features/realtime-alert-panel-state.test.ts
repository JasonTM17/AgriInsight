import { afterEach, describe, expect, it, vi } from "vitest";

import {
  acknowledgeRealtimeOperationalAlert,
  getRealtimeOperationalAlerts
} from "@/features/realtime-alerts/realtime-alert-client";
import {
  beginRealtimeAlertAcknowledgement,
  createRealtimeAlertPanelState,
  getRealtimeAlertAcknowledgementKey,
  getRealtimeAlertAcknowledgement,
  isRealtimeAlertPollingEligible,
  markRealtimeAlertAcknowledgementUnknown,
  receiveRealtimeAlertFeed
} from "@/features/realtime-alerts/realtime-alert-panel-state";
import type {
  RealtimeOperationalAlert,
  RealtimeOperationalAlertFeed
} from "@/features/realtime-alerts/realtime-alert-contract";

const alertId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
const eventId = "4fc03f21-71ee-46dc-a271-8d67ad4369c5";
const generatedAt = "2027-09-01T03:00:00Z";

describe("realtime alert client and panel state", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("gets the fixed same-origin feed with no query controls", async () => {
    const controller = new AbortController();
    const fetchStub = vi.fn().mockResolvedValue(Response.json(feed()));
    vi.stubGlobal("fetch", fetchStub);

    const result = await getRealtimeOperationalAlerts(controller.signal);

    expect(result).toEqual({ data: feed(), ok: true });
    expect(fetchStub).toHaveBeenCalledWith("/api/realtime/alerts", {
      cache: "no-store",
      credentials: "same-origin",
      signal: controller.signal
    });
  });

  it("fails before acknowledgement fetch when the CSRF cookie is unavailable", async () => {
    const fetchStub = vi.fn();
    vi.stubGlobal("document", { cookie: "session=opaque" });
    vi.stubGlobal("fetch", fetchStub);

    const result = await acknowledgeRealtimeOperationalAlert(alertId, "retry-key");

    expect(result).toMatchObject({
      ambiguous: false,
      ok: false,
      problem: { code: "missing_csrf", status: 403 }
    });
    expect(fetchStub).not.toHaveBeenCalled();
  });

  it("posts only the exact acknowledgement body and required same-origin headers", async () => {
    const controller = new AbortController();
    const csrfToken = "csrf-test-value";
    const fetchStub = vi.fn().mockResolvedValue(Response.json(alert(true)));
    vi.stubGlobal("document", {
      cookie: `other=value; __Host-agriinsight-csrf=${csrfToken}`
    });
    vi.stubGlobal("fetch", fetchStub);

    const result = await acknowledgeRealtimeOperationalAlert(
      alertId,
      "caller-stable-key",
      controller.signal
    );

    expect(result).toEqual({ data: alert(true), ok: true });
    expect(fetchStub).toHaveBeenCalledWith(
      `/api/realtime/alerts/${alertId}/acknowledgements`,
      {
        body: "{}",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": "caller-stable-key",
          "X-AgriInsight-Csrf": csrfToken
        },
        method: "POST",
        signal: controller.signal
      }
    );
  });

  it("fails closed when a successful response violates the strict contract", async () => {
    const fetchStub = vi.fn().mockResolvedValue(Response.json({ secret: "raw body" }));
    vi.stubGlobal("fetch", fetchStub);

    const result = await getRealtimeOperationalAlerts();

    expect(result).toMatchObject({
      ambiguous: false,
      ok: false,
      problem: { code: "invalid_response", status: 502 }
    });
    expect(JSON.stringify(result)).not.toContain("raw body");
  });

  it("keeps an acknowledgement retry ambiguous after a sanitized server failure", async () => {
    const fetchStub = vi.fn().mockResolvedValue(
      Response.json({ detail: "raw upstream secret" }, { status: 502 })
    );
    vi.stubGlobal("document", { cookie: "__Host-agriinsight-csrf=test-csrf" });
    vi.stubGlobal("fetch", fetchStub);

    const result = await acknowledgeRealtimeOperationalAlert(alertId, "retry-key");

    expect(result).toMatchObject({
      ambiguous: true,
      ok: false,
      problem: { code: "realtime_alert_unavailable", status: 502 }
    });
    expect(JSON.stringify(result)).not.toContain("raw upstream secret");
  });

  it("represents a stale bounded window without exposing a load-more state", () => {
    const state = receiveRealtimeAlertFeed(
      createRealtimeAlertPanelState(),
      feed({ hasMore: true }),
      Date.parse(generatedAt) + 60_001,
      60_000
    );

    expect(state.status).toBe("partial");
    expect(state.feed).toMatchObject({ isPartial: true, isStale: true });
  });

  it("retains an ambiguous acknowledgement key for the retry", () => {
    const loaded = receiveRealtimeAlertFeed(
      createRealtimeAlertPanelState(),
      feed(),
      Date.parse(generatedAt),
      60_000
    );
    const inFlight = beginRealtimeAlertAcknowledgement(
      loaded,
      alertId,
      "first-key"
    );
    const unknown = markRealtimeAlertAcknowledgementUnknown(inFlight, alertId);
    const retry = beginRealtimeAlertAcknowledgement(unknown, alertId, "second-key");

    expect(getRealtimeAlertAcknowledgementKey(retry, alertId)).toBe("first-key");
    expect(getRealtimeAlertAcknowledgement(retry, alertId)).toEqual({
      idempotencyKey: "first-key",
      status: "acknowledging"
    });
  });

  it("allows polling only while the panel is open, visible, and idle", () => {
    expect(isRealtimeAlertPollingEligible(true, "visible")).toBe(true);
    expect(isRealtimeAlertPollingEligible(false, "visible")).toBe(false);
    expect(isRealtimeAlertPollingEligible(true, "hidden")).toBe(false);
    expect(isRealtimeAlertPollingEligible(true, "visible", true)).toBe(false);
  });
});

function feed(
  overrides: Readonly<{ hasMore?: boolean }> = {}
): RealtimeOperationalAlertFeed {
  return {
    generatedAt,
    hasMore: overrides.hasMore ?? false,
    items: [alert(false)],
    limit: 50
  };
}

function alert(acknowledged: boolean): RealtimeOperationalAlert {
  return {
    acknowledged,
    acknowledgedAt: acknowledged ? "2027-09-01T02:59:55Z" : null,
    ageSeconds: 30,
    evidence: { id: eventId, type: "OPERATIONAL_EVENT" },
    id: alertId,
    lastEvaluatedAt: generatedAt,
    lastObservedAt: "2027-09-01T02:59:30Z",
    openedAt: "2027-09-01T02:58:00Z",
    policy: "REALTIME_DLT_RECORD",
    severity: "CRITICAL",
    source: "realtime_operational",
    sourceOccurredAt: "2027-09-01T02:58:00Z",
    state: "OPEN"
  };
}
