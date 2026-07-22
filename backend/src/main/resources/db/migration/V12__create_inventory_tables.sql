CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    location_text VARCHAR(240),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_warehouses_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ux_warehouses_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_warehouses_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT warehouses_code_canonical
        CHECK (code = upper(btrim(code)) AND code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT warehouses_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT warehouses_location_nonblank
        CHECK (location_text IS NULL OR btrim(location_text) <> ''),
    CONSTRAINT warehouses_version_nonnegative CHECK (version >= 0),
    CONSTRAINT warehouses_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_warehouses_tenant_active_code
    ON warehouses (tenant_id, active, code);

CREATE TABLE materials (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    base_unit VARCHAR(16) NOT NULL,
    minimum_stock_quantity NUMERIC(20, 4),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_materials_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ux_materials_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_materials_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT materials_code_canonical
        CHECK (code = upper(btrim(code)) AND code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT materials_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT materials_base_unit CHECK (base_unit IN ('KG', 'LITER', 'PIECE')),
    CONSTRAINT materials_minimum_stock_nonnegative
        CHECK (minimum_stock_quantity IS NULL OR minimum_stock_quantity >= 0),
    CONSTRAINT materials_version_nonnegative CHECK (version >= 0),
    CONSTRAINT materials_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_materials_tenant_active_code
    ON materials (tenant_id, active, code);

CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_suppliers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ux_suppliers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_suppliers_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT suppliers_code_canonical
        CHECK (code = upper(btrim(code)) AND code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT suppliers_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT suppliers_version_nonnegative CHECK (version >= 0),
    CONSTRAINT suppliers_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_suppliers_tenant_active_code
    ON suppliers (tenant_id, active, code);

CREATE TABLE user_warehouse_assignments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_profile_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_warehouse_assignment_profile
        FOREIGN KEY (tenant_id, user_profile_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT fk_user_warehouse_assignment_warehouse
        FOREIGN KEY (tenant_id, warehouse_id)
        REFERENCES warehouses (tenant_id, id),
    CONSTRAINT ux_user_warehouse_assignments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT user_warehouse_assignment_version_nonnegative CHECK (version >= 0),
    CONSTRAINT user_warehouse_assignment_revocation_order
        CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT user_warehouse_assignment_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX ux_user_warehouse_assignment_active
    ON user_warehouse_assignments (tenant_id, user_profile_id, warehouse_id)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_user_warehouse_assignment_warehouse_active
    ON user_warehouse_assignments (tenant_id, warehouse_id, user_profile_id)
    WHERE revoked_at IS NULL;

CREATE TABLE inventory_transactions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    material_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    unit_code VARCHAR(16) NOT NULL,
    quantity_base NUMERIC(20, 4) NOT NULL,
    signed_quantity_effect NUMERIC(20, 4) NOT NULL,
    unit_cost_vnd NUMERIC(18, 2),
    procurement_effect_vnd NUMERIC(20, 2) NOT NULL DEFAULT 0,
    supplier_id UUID,
    batch_code VARCHAR(64),
    expiry_date DATE,
    occurred_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500),
    reference_code VARCHAR(128),
    reversal_of UUID,
    recorded_by_profile_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_transactions_warehouse
        FOREIGN KEY (tenant_id, warehouse_id)
        REFERENCES warehouses (tenant_id, id),
    CONSTRAINT fk_inventory_transactions_material
        FOREIGN KEY (tenant_id, material_id)
        REFERENCES materials (tenant_id, id),
    CONSTRAINT fk_inventory_transactions_supplier
        FOREIGN KEY (tenant_id, supplier_id)
        REFERENCES suppliers (tenant_id, id),
    CONSTRAINT fk_inventory_transactions_recorder
        FOREIGN KEY (tenant_id, recorded_by_profile_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT ux_inventory_transactions_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_inventory_transactions_tenant_hierarchy_id
        UNIQUE (tenant_id, warehouse_id, material_id, id),
    CONSTRAINT fk_inventory_transactions_reversal
        FOREIGN KEY (tenant_id, reversal_of)
        REFERENCES inventory_transactions (tenant_id, id),
    CONSTRAINT inventory_transactions_kind
        CHECK (kind IN ('RECEIPT', 'ISSUE', 'REVERSAL')),
    CONSTRAINT inventory_transactions_unit
        CHECK (unit_code IN ('KG', 'LITER', 'PIECE')),
    CONSTRAINT inventory_transactions_quantity_positive CHECK (quantity_base > 0),
    CONSTRAINT inventory_transactions_signed_quantity
        CHECK (signed_quantity_effect <> 0 AND abs(signed_quantity_effect) = quantity_base),
    CONSTRAINT inventory_transactions_unit_cost_nonnegative
        CHECK (unit_cost_vnd IS NULL OR unit_cost_vnd >= 0),
    CONSTRAINT inventory_transactions_batch_nonblank
        CHECK (batch_code IS NULL OR btrim(batch_code) <> ''),
    CONSTRAINT inventory_transactions_reason_nonblank
        CHECK (reason IS NULL OR btrim(reason) <> ''),
    CONSTRAINT inventory_transactions_reference_nonblank
        CHECK (reference_code IS NULL OR btrim(reference_code) <> ''),
    CONSTRAINT inventory_transactions_not_self_reversal
        CHECK (reversal_of IS NULL OR reversal_of <> id),
    CONSTRAINT inventory_transactions_shape CHECK (
        (kind = 'RECEIPT'
            AND signed_quantity_effect > 0
            AND supplier_id IS NOT NULL
            AND batch_code IS NOT NULL
            AND expiry_date IS NOT NULL
            AND unit_cost_vnd IS NOT NULL
            AND procurement_effect_vnd >= 0
            AND reversal_of IS NULL)
        OR
        (kind = 'ISSUE'
            AND signed_quantity_effect < 0
            AND supplier_id IS NULL
            AND batch_code IS NULL
            AND expiry_date IS NULL
            AND unit_cost_vnd IS NULL
            AND procurement_effect_vnd = 0
            AND reason IS NOT NULL
            AND reversal_of IS NULL)
        OR
        (kind = 'REVERSAL'
            AND reversal_of IS NOT NULL
            AND reason IS NOT NULL)
    ),
    CONSTRAINT inventory_transactions_version_nonnegative CHECK (version >= 0),
    CONSTRAINT inventory_transactions_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_inventory_transactions_tenant_warehouse_occurred
    ON inventory_transactions (tenant_id, warehouse_id, occurred_at DESC, id);
CREATE INDEX ix_inventory_transactions_tenant_material_occurred
    ON inventory_transactions (tenant_id, material_id, occurred_at DESC, id);
CREATE INDEX ix_inventory_transactions_tenant_reversal
    ON inventory_transactions (tenant_id, reversal_of)
    WHERE reversal_of IS NOT NULL;

CREATE TABLE stock_lots (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    material_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    original_receipt_id UUID NOT NULL,
    batch_code VARCHAR(64) NOT NULL,
    expiry_date DATE NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    unit_code VARCHAR(16) NOT NULL,
    received_quantity NUMERIC(20, 4) NOT NULL,
    available_quantity NUMERIC(20, 4) NOT NULL,
    unit_cost_vnd NUMERIC(18, 2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_lots_warehouse
        FOREIGN KEY (tenant_id, warehouse_id)
        REFERENCES warehouses (tenant_id, id),
    CONSTRAINT fk_stock_lots_material
        FOREIGN KEY (tenant_id, material_id)
        REFERENCES materials (tenant_id, id),
    CONSTRAINT fk_stock_lots_supplier
        FOREIGN KEY (tenant_id, supplier_id)
        REFERENCES suppliers (tenant_id, id),
    CONSTRAINT fk_stock_lots_original_receipt
        FOREIGN KEY (tenant_id, warehouse_id, material_id, original_receipt_id)
        REFERENCES inventory_transactions (tenant_id, warehouse_id, material_id, id),
    CONSTRAINT ux_stock_lots_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_stock_lots_tenant_hierarchy_id
        UNIQUE (tenant_id, warehouse_id, material_id, id),
    CONSTRAINT ux_stock_lots_original_receipt UNIQUE (tenant_id, original_receipt_id),
    CONSTRAINT stock_lots_batch_nonblank CHECK (btrim(batch_code) <> ''),
    CONSTRAINT stock_lots_unit CHECK (unit_code IN ('KG', 'LITER', 'PIECE')),
    CONSTRAINT stock_lots_received_quantity_positive CHECK (received_quantity > 0),
    CONSTRAINT stock_lots_available_quantity_range
        CHECK (available_quantity >= 0 AND available_quantity <= received_quantity),
    CONSTRAINT stock_lots_unit_cost_nonnegative CHECK (unit_cost_vnd >= 0),
    CONSTRAINT stock_lots_version_nonnegative CHECK (version >= 0),
    CONSTRAINT stock_lots_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_stock_lots_tenant_warehouse_material_fefo
    ON stock_lots (
        tenant_id, warehouse_id, material_id, expiry_date, received_at, id)
    WHERE available_quantity > 0;
CREATE INDEX ix_stock_lots_tenant_expiry
    ON stock_lots (tenant_id, warehouse_id, expiry_date, id)
    WHERE available_quantity > 0;

CREATE TABLE inventory_transaction_lot_allocations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    stock_lot_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    material_id UUID NOT NULL,
    quantity_base NUMERIC(20, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_allocations_transaction
        FOREIGN KEY (tenant_id, warehouse_id, material_id, transaction_id)
        REFERENCES inventory_transactions (tenant_id, warehouse_id, material_id, id),
    CONSTRAINT fk_inventory_allocations_lot
        FOREIGN KEY (tenant_id, warehouse_id, material_id, stock_lot_id)
        REFERENCES stock_lots (tenant_id, warehouse_id, material_id, id),
    CONSTRAINT ux_inventory_allocations_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_inventory_allocations_transaction_lot
        UNIQUE (tenant_id, transaction_id, stock_lot_id),
    CONSTRAINT inventory_allocations_quantity_positive CHECK (quantity_base > 0)
);

CREATE INDEX ix_inventory_allocations_tenant_transaction
    ON inventory_transaction_lot_allocations (tenant_id, transaction_id, stock_lot_id);
CREATE INDEX ix_inventory_allocations_tenant_lot
    ON inventory_transaction_lot_allocations (tenant_id, stock_lot_id, transaction_id);

CREATE TABLE stock_balances (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    material_id UUID NOT NULL,
    unit_code VARCHAR(16) NOT NULL,
    quantity_on_hand NUMERIC(20, 4) NOT NULL DEFAULT 0,
    inventory_value_vnd NUMERIC(20, 2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_balances_warehouse
        FOREIGN KEY (tenant_id, warehouse_id)
        REFERENCES warehouses (tenant_id, id),
    CONSTRAINT fk_stock_balances_material
        FOREIGN KEY (tenant_id, material_id)
        REFERENCES materials (tenant_id, id),
    CONSTRAINT ux_stock_balances_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_stock_balances_hierarchy UNIQUE (tenant_id, warehouse_id, material_id),
    CONSTRAINT stock_balances_unit CHECK (unit_code IN ('KG', 'LITER', 'PIECE')),
    CONSTRAINT stock_balances_quantity_nonnegative CHECK (quantity_on_hand >= 0),
    CONSTRAINT stock_balances_value_nonnegative CHECK (inventory_value_vnd >= 0),
    CONSTRAINT stock_balances_version_nonnegative CHECK (version >= 0),
    CONSTRAINT stock_balances_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_stock_balances_tenant_warehouse_material
    ON stock_balances (tenant_id, warehouse_id, material_id);
CREATE INDEX ix_stock_balances_tenant_material_warehouse
    ON stock_balances (tenant_id, material_id, warehouse_id);
