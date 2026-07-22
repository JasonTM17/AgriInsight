package com.agriinsight.backend.inventory.application;

public record InventoryReconciliationReport(
        long checkedTransactionCount,
        long transactionDriftCount,
        long checkedLotCount,
        long lotDriftCount,
        long checkedBalanceCount,
        long balanceDriftCount) {

    public InventoryReconciliationReport {
        if (checkedTransactionCount < 0 || transactionDriftCount < 0
                || checkedLotCount < 0 || lotDriftCount < 0
                || checkedBalanceCount < 0 || balanceDriftCount < 0
                || transactionDriftCount > checkedTransactionCount
                || lotDriftCount > checkedLotCount
                || balanceDriftCount > checkedBalanceCount) {
            throw new IllegalArgumentException("reconciliation counts are invalid");
        }
    }

    public boolean consistent() {
        return transactionDriftCount == 0
                && lotDriftCount == 0
                && balanceDriftCount == 0;
    }
}
