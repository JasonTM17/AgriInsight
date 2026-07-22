package com.agriinsight.backend.integration.application;

import com.agriinsight.backend.integration.domain.OutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface OutboxStore {

    List<OutboxDrainService.OutboxLease> lease(
            String owner, int limit, Duration leaseDuration, Instant now);

    boolean acknowledge(OutboxDrainService.OutboxLease lease, Instant now);

    OutboxDrainService.FailureResult fail(
            OutboxDrainService.OutboxLease lease, String error, Instant now, Duration backoff);
}
