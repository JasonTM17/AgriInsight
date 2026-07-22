package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class InventoryReadService {

    private final PermissionEvaluator permissions;
    private final InventoryReadStore store;

    public InventoryReadService(PermissionEvaluator permissions, InventoryReadStore store) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
    }

    public InventoryTransactionPage listTransactions(InventoryTransactionQuery query) {
        return store.findTransactions(readScope(), Objects.requireNonNull(query, "query is required"));
    }

    public InventoryTransactionRecord getTransaction(UUID transactionId) {
        return store.findTransaction(
                        readScope(),
                        Objects.requireNonNull(transactionId, "transactionId is required"))
                .orElseThrow(() -> new ResourceNotFoundException("Inventory transaction"));
    }

    public StockBalancePage listBalances(StockBalanceQuery query) {
        return store.findBalances(readScope(), Objects.requireNonNull(query, "query is required"));
    }

    public StockLotPage listLots(StockLotQuery query) {
        return store.findLots(readScope(), Objects.requireNonNull(query, "query is required"));
    }

    private ScopeContext readScope() {
        return permissions.requireDomainList(
                Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE);
    }
}
