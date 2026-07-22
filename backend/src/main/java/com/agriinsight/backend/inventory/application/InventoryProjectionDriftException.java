package com.agriinsight.backend.inventory.application;

public class InventoryProjectionDriftException extends IllegalStateException {

    private final InventoryReconciliationReport report;

    public InventoryProjectionDriftException(InventoryReconciliationReport report) {
        super("Inventory projection reconciliation detected drift");
        this.report = report;
    }

    public InventoryReconciliationReport report() {
        return report;
    }
}
