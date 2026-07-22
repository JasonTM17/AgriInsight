package com.agriinsight.backend.persistence.support;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public final class InventoryQueryPlanTestSupport {

    private InventoryQueryPlanTestSupport() {
    }

    public static List<String> explain(Connection connection, String query) throws Exception {
        List<String> plan = new ArrayList<>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("EXPLAIN (COSTS OFF) " + query)) {
            while (rows.next()) {
                plan.add(rows.getString(1));
            }
        }
        return plan;
    }
}
