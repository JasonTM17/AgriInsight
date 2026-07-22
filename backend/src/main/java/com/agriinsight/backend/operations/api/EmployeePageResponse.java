package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.EmployeePage;
import java.util.List;
import java.util.Objects;

public record EmployeePageResponse(
        List<EmployeeResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public EmployeePageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static EmployeePageResponse from(EmployeePage page) {
        Objects.requireNonNull(page, "page is required");
        return new EmployeePageResponse(
                page.items().stream().map(EmployeeResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
