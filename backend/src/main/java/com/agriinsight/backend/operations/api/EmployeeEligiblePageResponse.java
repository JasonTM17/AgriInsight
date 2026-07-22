package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.EmployeePage;
import java.util.List;
import java.util.Objects;

public record EmployeeEligiblePageResponse(
        List<EmployeeEligibleResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public EmployeeEligiblePageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static EmployeeEligiblePageResponse from(EmployeePage page) {
        Objects.requireNonNull(page, "page is required");
        return new EmployeeEligiblePageResponse(
                page.items().stream().map(EmployeeEligibleResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
