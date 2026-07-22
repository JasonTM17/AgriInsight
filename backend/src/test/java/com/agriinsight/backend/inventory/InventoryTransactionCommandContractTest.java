package com.agriinsight.backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.domain.InventoryNumbers;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryTransactionCommandContractTest {

    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID MATERIAL_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2027-01-15T08:30:00Z");
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());

    @Test
    void receiptNormalizesPrecisionAndDerivesRoundedProcurementEffect() {
        var receipt = new InventoryTransactionCommands.Receipt(
                WAREHOUSE_ID,
                MATERIAL_ID,
                SUPPLIER_ID,
                new BigDecimal("2.5000"),
                new BigDecimal("10.23"),
                " Batch A ",
                LocalDate.parse("2027-06-30"),
                OCCURRED_AT,
                Optional.of(" PO-100 "),
                AUDIT);

        assertThat(receipt.kind()).isEqualTo(InventoryTransactionKind.RECEIPT);
        assertThat(receipt.quantityBase()).isEqualByComparingTo("2.5");
        assertThat(receipt.batchCode()).isEqualTo("Batch A");
        assertThat(receipt.referenceCode()).contains("PO-100");
        assertThat(receipt.procurementEffectVnd()).isEqualByComparingTo("25.58");
    }

    @Test
    void issueShapeCannotAcceptSupplierOrClientSuppliedFinance() {
        var issue = new InventoryTransactionCommands.Issue(
                WAREHOUSE_ID,
                MATERIAL_ID,
                new BigDecimal("1.25"),
                Optional.empty(),
                OCCURRED_AT,
                " Field consumption ",
                Optional.empty(),
                AUDIT);

        assertThat(issue.kind()).isEqualTo(InventoryTransactionKind.ISSUE);
        assertThat(issue.reason()).isEqualTo("Field consumption");
        assertThat(InventoryTransactionCommands.Issue.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("supplierId", "unitCostVnd", "procurementEffectVnd");
    }

    @Test
    void rejectsExpiredReceiptAndUnsupportedPrecision() {
        assertThatThrownBy(() -> receipt(LocalDate.parse("2027-01-14")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiryDate");
        assertThatThrownBy(() -> InventoryNumbers.positiveQuantity(new BigDecimal("1.00001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precision");
        assertThatThrownBy(() -> InventoryNumbers.nonnegativeUnitCost(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void reversalRequiresPositiveBoundedQuantityAndVersion() {
        var reversal = new InventoryTransactionCommands.Reversal(
                new BigDecimal("0.5000"), " Partial correction ", 3, AUDIT);

        assertThat(reversal.quantityBase()).isEqualByComparingTo("0.5");
        assertThat(reversal.reason()).isEqualTo("Partial correction");
        assertThat(reversal.expectedVersion()).isEqualTo(3);
        assertThatThrownBy(() -> new InventoryTransactionCommands.Reversal(
                BigDecimal.ZERO, "Correction", 0, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    private InventoryTransactionCommands.Receipt receipt(LocalDate expiryDate) {
        return new InventoryTransactionCommands.Receipt(
                WAREHOUSE_ID, MATERIAL_ID, SUPPLIER_ID,
                BigDecimal.ONE, BigDecimal.TEN, "Batch", expiryDate,
                OCCURRED_AT, Optional.empty(), AUDIT);
    }
}
