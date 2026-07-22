package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryReadStore;
import com.agriinsight.backend.inventory.application.InventoryTransactionPage;
import com.agriinsight.backend.inventory.application.InventoryTransactionQuery;
import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import com.agriinsight.backend.inventory.application.StockBalancePage;
import com.agriinsight.backend.inventory.application.StockBalanceQuery;
import com.agriinsight.backend.inventory.application.StockLotPage;
import com.agriinsight.backend.inventory.application.StockLotQuery;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresInventoryReadStore implements InventoryReadStore {

    private final PostgresInventoryTransactionQueries transactions;
    private final PostgresStockBalanceQueries balances;
    private final PostgresStockLotQueries lots;

    public PostgresInventoryReadStore(JdbcTemplate jdbcTemplate) {
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.transactions = new PostgresInventoryTransactionQueries(jdbcTemplate);
        this.balances = new PostgresStockBalanceQueries(jdbcTemplate);
        this.lots = new PostgresStockLotQueries(jdbcTemplate);
    }

    @Override
    public InventoryTransactionPage findTransactions(
            ScopeContext scope, InventoryTransactionQuery query) {
        return transactions.findAll(required(scope), query);
    }

    @Override
    public Optional<InventoryTransactionRecord> findTransaction(
            ScopeContext scope, UUID transactionId) {
        return transactions.findById(required(scope), transactionId);
    }

    @Override
    public StockBalancePage findBalances(ScopeContext scope, StockBalanceQuery query) {
        return balances.findAll(required(scope), query);
    }

    @Override
    public StockLotPage findLots(ScopeContext scope, StockLotQuery query) {
        return lots.findAll(required(scope), query);
    }

    private ScopeContext required(ScopeContext scope) {
        return Objects.requireNonNull(scope, "scope is required");
    }
}
