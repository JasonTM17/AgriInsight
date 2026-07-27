package com.agriinsight.backend.integration.application;

import com.agriinsight.backend.integration.domain.OutboxEvent;

@FunctionalInterface
public interface OperationalEventPublisher {

    void publish(OutboxEvent event);
}
