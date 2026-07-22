package com.agriinsight.backend.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import com.agriinsight.backend.inventory.application.StockBalanceRecord;
import com.agriinsight.backend.inventory.application.StockLotRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class InventoryHttpTestSupport {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID WAREHOUSE_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    static final UUID MATERIAL_ID = UUID.fromString("52000000-0000-0000-0000-000000000001");
    static final UUID SUPPLIER_ID = UUID.fromString("54000000-0000-0000-0000-000000000001");
    static final UUID TRANSACTION_ID = UUID.fromString("58000000-0000-0000-0000-000000000001");
    static final UUID REVERSAL_ID = UUID.fromString("58000000-0000-0000-0000-000000000002");
    static final String TOKEN = "inventory-api-token";
    static final String AUTHORIZATION = "Bearer " + TOKEN;

    private InventoryHttpTestSupport() {
    }

    static void stubIdentity(
            JwtDecoder decoder,
            TenantPrincipalLoader principalLoader,
            Set<Permission> permissions) {
        when(decoder.decode(TOKEN)).thenReturn(jwt());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                ACTOR_ID, TENANT_ID, "TENANT-A", Optional.of("Inventory Admin"),
                Optional.empty(), Optional.of("mfa"), Set.of(Role.INVENTORY_MANAGER),
                permissions));
    }

    static InventoryTransactionRecord receipt() {
        return new InventoryTransactionRecord(
                TRANSACTION_ID, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID,
                InventoryTransactionKind.RECEIPT, CanonicalUnit.KG,
                new BigDecimal("2.5"), new BigDecimal("2.5"),
                Optional.of(new BigDecimal("100")), new BigDecimal("250"),
                Optional.of(SUPPLIER_ID), Optional.of("BATCH-A"),
                Optional.of(LocalDate.parse("2027-12-31")),
                Instant.parse("2027-01-01T08:00:00Z"), Optional.empty(),
                Optional.of("PO-1"), Optional.empty(), ACTOR_ID, 0);
    }

    static InventoryTransactionRecord reversal() {
        return new InventoryTransactionRecord(
                REVERSAL_ID, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID,
                InventoryTransactionKind.REVERSAL, CanonicalUnit.KG,
                BigDecimal.ONE, BigDecimal.ONE.negate(),
                Optional.of(new BigDecimal("100")), new BigDecimal("-100"),
                Optional.of(SUPPLIER_ID), Optional.of("BATCH-A"),
                Optional.of(LocalDate.parse("2027-12-31")), Instant.now(),
                Optional.of("Correction"), Optional.empty(), Optional.of(TRANSACTION_ID),
                ACTOR_ID, 0);
    }

    static StockBalanceRecord balance() {
        return new StockBalanceRecord(
                UUID.fromString("57000000-0000-0000-0000-000000000001"),
                WAREHOUSE_ID, "WH-A", MATERIAL_ID, "FERT-A", "Fertilizer",
                CanonicalUnit.KG, new BigDecimal("5"), new BigDecimal("500"),
                Optional.of(new BigDecimal("10")), true, 3);
    }

    static StockLotRecord lot() {
        return new StockLotRecord(
                UUID.fromString("56000000-0000-0000-0000-000000000001"),
                WAREHOUSE_ID, "WH-A", MATERIAL_ID, "FERT-A", "Fertilizer",
                SUPPLIER_ID, "SUP-A", TRANSACTION_ID, "BATCH-A",
                LocalDate.parse("2027-12-31"), Instant.parse("2027-01-01T08:00:00Z"),
                CanonicalUnit.KG, new BigDecimal("5"), new BigDecimal("5"),
                new BigDecimal("100"), false, false, 0);
    }

    static CommandExecutionResult.Completed<InventoryTransactionRecord> completed(
            InventoryTransactionRecord transaction) {
        return new CommandExecutionResult.Completed<>(
                UUID.randomUUID(), false, 201,
                new CommandTarget("INVENTORY_TRANSACTION", transaction.id(), transaction.version()),
                Optional.of(transaction));
    }

    private static Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("inventory-manager")
                .audience(java.util.List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access")
                .build();
    }
}
