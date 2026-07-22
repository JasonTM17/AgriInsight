package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import com.agriinsight.backend.inventory.application.InventoryTransactionStore;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
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
public class PostgresInventoryTransactionStore implements InventoryTransactionStore {

    private final InventoryPostingTargetLocks targets;
    private final PostgresInventoryLedger ledger;
    private final PostgresInventoryReceiptPoster receipts;
    private final PostgresInventoryIssuePoster issues;
    private final PostgresInventoryReversalPoster reversals;

    public PostgresInventoryTransactionStore(JdbcTemplate jdbcTemplate) {
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.targets = new InventoryPostingTargetLocks(jdbcTemplate);
        this.ledger = new PostgresInventoryLedger(jdbcTemplate);
        PostgresInventoryBalanceProjection balances =
                new PostgresInventoryBalanceProjection(jdbcTemplate);
        this.receipts = new PostgresInventoryReceiptPoster(jdbcTemplate, ledger, balances);
        this.issues = new PostgresInventoryIssuePoster(jdbcTemplate, ledger, balances);
        this.reversals = new PostgresInventoryReversalPoster(jdbcTemplate, ledger, balances);
    }

    @Override
    public Optional<InventoryTransactionRecord> findById(
            ScopeContext scope, UUID transactionId) {
        return ledger.find(
                Objects.requireNonNull(scope, "scope is required"), transactionId, false);
    }

    @Override
    public boolean postingTargetAvailable(
            ScopeContext scope, InventoryTransactionCommands.Posting command) {
        return targets.available(scope, command);
    }

    @Override
    public InventoryTransactionRecord post(
            ScopeContext scope,
            UUID transactionId,
            InventoryTransactionCommands.Posting command) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        UUID target = Objects.requireNonNull(transactionId, "transactionId is required");
        CanonicalUnit unit = targets.lockPosting(required, command);
        if (command instanceof InventoryTransactionCommands.Receipt receipt) {
            return receipts.post(required, target, unit, receipt);
        }
        if (command instanceof InventoryTransactionCommands.Issue issue) {
            return issues.post(required, target, unit, issue);
        }
        throw new IllegalArgumentException("Unsupported inventory posting kind");
    }

    @Override
    public InventoryTransactionRecord reverse(
            ScopeContext scope,
            UUID originalTransactionId,
            UUID reversalTransactionId,
            InventoryTransactionCommands.Reversal command) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        InventoryTransactionRecord original = ledger.find(
                        required,
                        Objects.requireNonNull(originalTransactionId,
                                "originalTransactionId is required"),
                        false)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory transaction"));
        targets.lockWarehouseAccess(required, original.warehouseId());
        return reversals.post(
                required,
                original.id(),
                Objects.requireNonNull(reversalTransactionId,
                        "reversalTransactionId is required"),
                Objects.requireNonNull(command, "command is required"));
    }
}
