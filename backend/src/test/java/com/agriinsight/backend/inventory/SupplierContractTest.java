package com.agriinsight.backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.application.SupplierCommands;
import com.agriinsight.backend.inventory.application.SupplierQuery;
import com.agriinsight.backend.inventory.domain.Supplier;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplierContractTest {

    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());

    @Test
    void canonicalizesSafeCatalogFields() {
        var supplier = new Supplier(
                UUID.randomUUID(), UUID.randomUUID(), " supplier.n ", " Supplier North ");

        assertThat(supplier.code()).isEqualTo("SUPPLIER.N");
        assertThat(supplier.displayName()).isEqualTo("Supplier North");
    }

    @Test
    void rejectsInvalidCodesAndEmptyUpdates() {
        assertThatThrownBy(() -> new Supplier(
                UUID.randomUUID(), UUID.randomUUID(), "supplier north", "Supplier North"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("format");
        assertThatThrownBy(() -> new SupplierCommands.Update(
                Optional.empty(), Optional.empty(), 0, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void queryNormalizesSearchAndEnforcesPageCaps() {
        var query = new SupplierQuery(100, 10_000, Optional.of(true), Optional.of(" north "));

        assertThat(query.search()).contains("north");
        assertThatThrownBy(() -> new SupplierQuery(
                101, 0, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }
}
