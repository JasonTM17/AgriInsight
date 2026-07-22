CREATE FUNCTION agriinsight_security.app_current_profile_id()
RETURNS UUID
LANGUAGE plpgsql
STABLE
PARALLEL SAFE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
DECLARE
    configured_profile TEXT;
BEGIN
    configured_profile := pg_catalog.current_setting('app.profile_id', TRUE);
    IF configured_profile IS NULL
       OR configured_profile = ''
       OR configured_profile !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN
        RETURN NULL;
    END IF;
    RETURN configured_profile::UUID;
EXCEPTION
    WHEN invalid_text_representation THEN
        RETURN NULL;
END
$function$;

CREATE FUNCTION agriinsight_security.inventory_warehouse_access(
    p_warehouse_id UUID,
    p_write BOOLEAN
)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $function$
    SELECT EXISTS (
        SELECT 1
          FROM public.warehouses AS warehouse
         WHERE warehouse.tenant_id = agriinsight_security.app_current_tenant_id()
           AND warehouse.id = p_warehouse_id
           AND EXISTS (
               SELECT 1
                 FROM public.user_roles AS role_assignment
                WHERE role_assignment.tenant_id = warehouse.tenant_id
                  AND role_assignment.user_profile_id =
                      agriinsight_security.app_current_profile_id()
                  AND role_assignment.revoked_at IS NULL
                  AND (
                      role_assignment.role_code = 'TENANT_ADMIN'
                      OR (NOT p_write AND role_assignment.role_code IN (
                          'EXECUTIVE', 'DATA_ANALYST'))
                      OR (role_assignment.role_code = 'INVENTORY_MANAGER'
                          AND EXISTS (
                              SELECT 1
                                FROM public.user_warehouse_assignments AS assignment
                               WHERE assignment.tenant_id = warehouse.tenant_id
                                 AND assignment.user_profile_id =
                                     role_assignment.user_profile_id
                                 AND assignment.warehouse_id = warehouse.id
                                 AND assignment.revoked_at IS NULL))
                      OR (NOT p_write AND role_assignment.role_code = 'FARM_MANAGER'
                          AND EXISTS (
                              SELECT 1
                                FROM public.user_warehouse_assignments AS assignment
                               WHERE assignment.tenant_id = warehouse.tenant_id
                                 AND assignment.user_profile_id =
                                     role_assignment.user_profile_id
                                 AND assignment.warehouse_id = warehouse.id
                                 AND assignment.revoked_at IS NULL))
                  )
           )
    )
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.app_current_profile_id() FROM PUBLIC;
REVOKE ALL ON FUNCTION agriinsight_security.inventory_warehouse_access(UUID, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION agriinsight_security.app_current_profile_id() TO agriinsight_runtime;
GRANT EXECUTE ON FUNCTION agriinsight_security.inventory_warehouse_access(UUID, BOOLEAN)
    TO agriinsight_runtime, agriinsight_migrator;

DROP POLICY runtime_tenant_isolation ON inventory_transactions;
CREATE POLICY runtime_inventory_read ON inventory_transactions
    FOR SELECT TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, FALSE));
CREATE POLICY runtime_inventory_insert ON inventory_transactions
    FOR INSERT TO agriinsight_runtime
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, TRUE));
CREATE POLICY runtime_inventory_update ON inventory_transactions
    FOR UPDATE TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, TRUE))
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, TRUE));

DROP POLICY runtime_tenant_isolation ON inventory_transaction_lot_allocations;
CREATE POLICY runtime_inventory_read ON inventory_transaction_lot_allocations
    FOR SELECT TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, FALSE));
CREATE POLICY runtime_inventory_insert ON inventory_transaction_lot_allocations
    FOR INSERT TO agriinsight_runtime
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, TRUE));

DROP POLICY runtime_tenant_isolation ON stock_lots;
CREATE POLICY runtime_inventory_read ON stock_lots
    FOR SELECT TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, FALSE));
CREATE POLICY runtime_inventory_insert ON stock_lots
    FOR INSERT TO agriinsight_runtime
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, TRUE));
CREATE POLICY runtime_inventory_update ON stock_lots
    FOR UPDATE TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, TRUE))
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, TRUE));

DROP POLICY runtime_tenant_isolation ON stock_balances;
CREATE POLICY runtime_inventory_read ON stock_balances
    FOR SELECT TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, FALSE));
CREATE POLICY runtime_inventory_insert ON stock_balances
    FOR INSERT TO agriinsight_runtime
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, TRUE));
CREATE POLICY runtime_inventory_update ON stock_balances
    FOR UPDATE TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, TRUE))
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.inventory_warehouse_access(warehouse_id, TRUE));

CREATE INDEX ix_inventory_transactions_tenant_occurred
    ON inventory_transactions (tenant_id, occurred_at DESC, id DESC);
CREATE INDEX ix_stock_lots_tenant_fefo_all
    ON stock_lots (tenant_id, expiry_date, received_at, id);
CREATE INDEX ix_stock_lots_tenant_warehouse_material_fefo_all
    ON stock_lots (tenant_id, warehouse_id, material_id, expiry_date, received_at, id);
