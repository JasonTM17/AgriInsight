package com.agriinsight.backend.integration.domain;

public enum OutboxStatus {
    PENDING,
    LEASED,
    PUBLISHED,
    DEAD_LETTER
}
