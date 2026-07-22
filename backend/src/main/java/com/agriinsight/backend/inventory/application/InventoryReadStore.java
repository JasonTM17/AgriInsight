package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReadStore {

    InventoryTransactionPage findTransactions(
            ScopeContext scope, InventoryTransactionQuery query);

    Optional<InventoryTransactionRecord> findTransaction(
            ScopeContext scope, UUID transactionId);

    StockBalancePage findBalances(ScopeContext scope, StockBalanceQuery query);

    StockLotPage findLots(ScopeContext scope, StockLotQuery query);
}
