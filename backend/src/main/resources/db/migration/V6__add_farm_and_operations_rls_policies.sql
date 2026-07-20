-- Tenant equality is a database backstop; farm/activity scope remains an application predicate.

ALTER TABLE farms ENABLE ROW LEVEL SECURITY;
ALTER TABLE farms FORCE ROW LEVEL SECURITY;
ALTER TABLE crops ENABLE ROW LEVEL SECURITY;
ALTER TABLE crops FORCE ROW LEVEL SECURITY;
ALTER TABLE fields ENABLE ROW LEVEL SECURITY;
ALTER TABLE fields FORCE ROW LEVEL SECURITY;
ALTER TABLE seasons ENABLE ROW LEVEL SECURITY;
ALTER TABLE seasons FORCE ROW LEVEL SECURITY;
ALTER TABLE employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE employees FORCE ROW LEVEL SECURITY;
ALTER TABLE user_farm_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_farm_assignments FORCE ROW LEVEL SECURITY;
ALTER TABLE activity_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE activity_types FORCE ROW LEVEL SECURITY;
ALTER TABLE activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE activities FORCE ROW LEVEL SECURITY;
ALTER TABLE activity_assignees ENABLE ROW LEVEL SECURITY;
ALTER TABLE activity_assignees FORCE ROW LEVEL SECURITY;
ALTER TABLE activity_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE activity_logs FORCE ROW LEVEL SECURITY;
ALTER TABLE harvests ENABLE ROW LEVEL SECURITY;
ALTER TABLE harvests FORCE ROW LEVEL SECURITY;

CREATE POLICY runtime_tenant_isolation ON farms
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON farms
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON crops
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON crops
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON fields
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON fields
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON seasons
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON seasons
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON employees
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON employees
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON user_farm_assignments
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON user_farm_assignments
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON activity_types
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON activity_types
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON activities
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON activities
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON activity_assignees
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON activity_assignees
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON activity_logs
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON activity_logs
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON harvests
    AS PERMISSIVE FOR ALL TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_tenant_isolation ON harvests
    AS PERMISSIVE FOR ALL TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
