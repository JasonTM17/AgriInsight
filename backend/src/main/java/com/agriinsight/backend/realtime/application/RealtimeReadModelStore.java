package com.agriinsight.backend.realtime.application;

/** Durable projection boundary for validated operational-event metadata. */
public interface RealtimeReadModelStore {

    ApplyResult apply(RealtimeOperationalEvent event);

    enum ApplyResult {
        APPLIED,
        DUPLICATE
    }
}
