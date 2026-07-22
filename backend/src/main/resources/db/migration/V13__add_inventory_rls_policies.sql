ALTER TABLE warehouses ENABLE ROW LEVEL SECURITY;
ALTER TABLE warehouses FORCE ROW LEVEL SECURITY;
ALTER TABLE materials ENABLE ROW LEVEL SECURITY;
ALTER TABLE materials FORCE ROW LEVEL SECURITY;
ALTER TABLE suppliers ENABLE ROW LEVEL SECURITY;
ALTER TABLE suppliers FORCE ROW LEVEL SECURITY;
ALTER TABLE user_warehouse_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_warehouse_assignments FORCE ROW LEVEL SECURITY;
ALTER TABLE inventory_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_transactions FORCE ROW LEVEL SECURITY;
ALTER TABLE inventory_transaction_lot_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_transaction_lot_allocations FORCE ROW LEVEL SECURITY;
ALTER TABLE stock_lots ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_lots FORCE ROW LEVEL SECURITY;
ALTER TABLE stock_balances ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_balances FORCE ROW LEVEL SECURITY;

CREATE POLICY runtime_tenant_isolation ON warehouses
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON warehouses
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON materials
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON materials
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON suppliers
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON suppliers
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON user_warehouse_assignments
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON user_warehouse_assignments
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON inventory_transactions
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON inventory_transactions
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON inventory_transaction_lot_allocations
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON inventory_transaction_lot_allocations
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON stock_lots
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON stock_lots
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON stock_balances
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON stock_balances
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
