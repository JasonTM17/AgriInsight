package com.agriinsight.backend.inventory.application;

public record InventoryReconciliationReport(
        long checkedLotCount,
        long lotDriftCount,
        long checkedBalanceCount,
        long balanceDriftCount) {

    public InventoryReconciliationReport {
        if (checkedLotCount < 0 || lotDriftCount < 0
                || checkedBalanceCount < 0 || balanceDriftCount < 0
                || lotDriftCount > checkedLotCount
                || balanceDriftCount > checkedBalanceCount) {
            throw new IllegalArgumentException("reconciliation counts are invalid");
        }
    }

    public boolean consistent() {
        return lotDriftCount == 0 && balanceDriftCount == 0;
    }
}
