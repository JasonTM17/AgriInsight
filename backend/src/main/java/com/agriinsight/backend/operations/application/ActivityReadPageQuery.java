package com.agriinsight.backend.operations.application;

public record ActivityReadPageQuery(int limit, int offset) {

    public ActivityReadPageQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0 || offset > 10_000) {
            throw new IllegalArgumentException("offset must be between 0 and 10000");
        }
    }
}
