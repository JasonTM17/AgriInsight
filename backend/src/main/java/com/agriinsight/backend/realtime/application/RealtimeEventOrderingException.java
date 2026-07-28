package com.agriinsight.backend.realtime.application;

/** Signals an aggregate event version that cannot advance the durable projection. */
public class RealtimeEventOrderingException extends RuntimeException {

    private final Reason reason;

    public RealtimeEventOrderingException(Reason reason) {
        super(messageFor(reason));
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    private static String messageFor(Reason reason) {
        return switch (reason) {
            case STALE -> "aggregate event version is stale";
            case GAP -> "aggregate event version contains a gap";
        };
    }

    public enum Reason {
        STALE,
        GAP
    }
}
