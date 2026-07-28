package com.agriinsight.backend.realtime.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;

/** Read boundary for a tenant-scoped, bounded realtime projection. */
public interface RealtimeSummaryStore {

    RealtimeSummary summarize(ScopeContext scope);
}
