package com.agriinsight.backend.operations.application;

import java.util.Objects;
import java.util.Optional;

public record EmployeeQuery(
        int limit,
        int offset,
        Optional<Boolean> active,
        Optional<String> search) {

    public EmployeeQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0 || offset > 10_000) {
            throw new IllegalArgumentException("offset must be between 0 and 10000");
        }
        active = Objects.requireNonNull(active, "active is required");
        search = Objects.requireNonNull(search, "search is required")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
        search.ifPresent(value -> {
            if (value.length() > 120) {
                throw new IllegalArgumentException("search must not exceed 120 characters");
            }
        });
    }
}
