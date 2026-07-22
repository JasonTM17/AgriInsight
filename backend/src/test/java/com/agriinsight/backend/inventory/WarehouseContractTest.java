package com.agriinsight.backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.ScopeResolver;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.WarehouseQuery;
import com.agriinsight.backend.inventory.domain.Warehouse;
import com.agriinsight.backend.inventory.infrastructure.InventoryDomainScopeResolver;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseContractTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private final InventoryDomainScopeResolver resolver = new InventoryDomainScopeResolver();

    @Test
    void warehouseNormalizesBusinessFieldsAndRejectsInvalidValues() {
        var warehouse = new Warehouse(
                WAREHOUSE_ID,
                TENANT_ID,
                " wh-01 ",
                " Kho vật tư Đắk Lắk ",
                Optional.of(" Buôn Ma Thuột "));

        assertThat(warehouse.code()).isEqualTo("WH-01");
        assertThat(warehouse.displayName()).isEqualTo("Kho vật tư Đắk Lắk");
        assertThat(warehouse.locationText()).contains("Buôn Ma Thuột");
        assertThatThrownBy(() -> Warehouse.canonicalCode("warehouse code"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code has an invalid format");
        assertThatThrownBy(() -> Warehouse.canonicalLocation("x".repeat(241)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("locationText must not exceed 240 characters");
    }

    @Test
    void warehouseQueryBoundsAndNormalizesSearch() {
        var query = new WarehouseQuery(50, 0, Optional.of(true), Optional.of("  Đắk Lắk  "));

        assertThat(query.search()).contains("Đắk Lắk");
        assertThatThrownBy(() -> new WarehouseQuery(101, 0, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
        assertThatThrownBy(() -> new WarehouseQuery(50, 10_001, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("offset must be between 0 and 10000");
    }

    @Test
    void inventoryRolesResolveOnlyTheirDeclaredWarehouseScope() {
        assertThat(access(Set.of(Role.EXECUTIVE), Permission.INVENTORY_READ, Optional.empty()))
                .isEqualTo(ScopeResolver.DomainAccess.TENANT_WIDE);
        assertThat(access(Set.of(Role.INVENTORY_MANAGER), Permission.INVENTORY_READ, Optional.empty()))
                .isEqualTo(ScopeResolver.DomainAccess.DOMAIN);
        assertThat(access(Set.of(Role.INVENTORY_MANAGER), Permission.INVENTORY_MANAGE,
                Optional.of(WAREHOUSE_ID)))
                .isEqualTo(ScopeResolver.DomainAccess.DOMAIN);
        assertThat(access(Set.of(Role.FARM_MANAGER), Permission.INVENTORY_READ,
                Optional.of(WAREHOUSE_ID)))
                .isEqualTo(ScopeResolver.DomainAccess.DOMAIN);
        assertThat(access(Set.of(Role.FARM_MANAGER), Permission.INVENTORY_MANAGE,
                Optional.of(WAREHOUSE_ID)))
                .isEqualTo(ScopeResolver.DomainAccess.DENIED);
        assertThat(access(Set.of(Role.SUPPLIER), Permission.INVENTORY_READ, Optional.empty()))
                .isEqualTo(ScopeResolver.DomainAccess.DENIED);
        assertThat(resolver.type()).isEqualTo(ScopeContext.Type.WAREHOUSE);
    }

    private ScopeResolver.DomainAccess access(
            Set<Role> roles,
            Permission permission,
            Optional<UUID> warehouseId) {
        return resolver.access(
                new TestPrincipal(PROFILE_ID, TENANT_ID),
                roles,
                permission,
                warehouseId);
    }

    private record TestPrincipal(UUID profileId, UUID tenantId)
            implements com.agriinsight.backend.shared.security.TenantPrincipal {

        @Override
        public String getName() {
            return profileId.toString();
        }
    }
}
