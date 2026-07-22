package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Optional;
import java.util.UUID;

public interface InventoryTransactionStore {

    Optional<InventoryTransactionRecord> findById(
            ScopeContext scope, UUID transactionId);

    boolean postingTargetAvailable(
            ScopeContext scope, InventoryTransactionCommands.Posting command);

    InventoryTransactionRecord post(
            ScopeContext scope,
            UUID transactionId,
            InventoryTransactionCommands.Posting command);

    InventoryTransactionRecord reverse(
            ScopeContext scope,
            UUID originalTransactionId,
            UUID reversalTransactionId,
            InventoryTransactionCommands.Reversal command);
}
