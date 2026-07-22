package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class InventoryTransactionRowMapping {

    static final String SELECT_COLUMNS = """
            transaction.id, transaction.tenant_id, transaction.warehouse_id,
            transaction.material_id, transaction.kind, transaction.unit_code,
            transaction.quantity_base, transaction.signed_quantity_effect,
            transaction.unit_cost_vnd, transaction.procurement_effect_vnd,
            transaction.supplier_id, transaction.batch_code, transaction.expiry_date,
            transaction.occurred_at, transaction.reason, transaction.reference_code,
            transaction.reversal_of, transaction.recorded_by_profile_id, transaction.version
            """;

    static final RowMapper<InventoryTransactionRecord> MAPPER =
            (result, rowNumber) -> map(result);

    private InventoryTransactionRowMapping() {
    }

    static Optional<InventoryTransactionRecord> exactlyOneOrEmpty(
            List<InventoryTransactionRecord> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Inventory transaction query returned multiple rows");
        }
        return rows.stream().findFirst();
    }

    private static InventoryTransactionRecord map(ResultSet result) throws SQLException {
        BigDecimal unitCost = result.getBigDecimal("unit_cost_vnd");
        return new InventoryTransactionRecord(
                result.getObject("id", UUID.class),
                result.getObject("tenant_id", UUID.class),
                result.getObject("warehouse_id", UUID.class),
                result.getObject("material_id", UUID.class),
                InventoryTransactionKind.valueOf(result.getString("kind")),
                CanonicalUnit.valueOf(result.getString("unit_code")),
                result.getBigDecimal("quantity_base"),
                result.getBigDecimal("signed_quantity_effect"),
                Optional.ofNullable(unitCost),
                result.getBigDecimal("procurement_effect_vnd"),
                Optional.ofNullable(result.getObject("supplier_id", UUID.class)),
                Optional.ofNullable(result.getString("batch_code")),
                Optional.ofNullable(result.getObject("expiry_date", LocalDate.class)),
                result.getTimestamp("occurred_at").toInstant(),
                Optional.ofNullable(result.getString("reason")),
                Optional.ofNullable(result.getString("reference_code")),
                Optional.ofNullable(result.getObject("reversal_of", UUID.class)),
                result.getObject("recorded_by_profile_id", UUID.class),
                result.getLong("version"));
    }
}
