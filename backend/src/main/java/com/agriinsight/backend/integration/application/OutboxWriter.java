package com.agriinsight.backend.integration.application;

import com.agriinsight.backend.shared.application.CommandCommittedEvent;

@FunctionalInterface
public interface OutboxWriter {

    void append(CommandCommittedEvent event);
}
