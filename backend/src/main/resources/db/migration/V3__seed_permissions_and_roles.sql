INSERT INTO permissions (code, display_name) VALUES
    ('IDENTITY_USER_MANAGE', 'Manage tenant identities'),
    ('IDENTITY_ROLE_MANAGE', 'Manage fixed tenant roles'),
    ('FARM_READ', 'Read farm data'),
    ('FARM_MANAGE', 'Manage farm data'),
    ('FARM_ASSIGNMENT_MANAGE', 'Manage farm assignments'),
    ('SEASON_READ', 'Read season data'),
    ('SEASON_MANAGE', 'Manage seasons'),
    ('WORKFORCE_MANAGE', 'Manage workforce records'),
    ('WORKFORCE_PICKER_READ', 'Read redacted workforce picker'),
    ('ACTIVITY_READ', 'Read activities'),
    ('ACTIVITY_MANAGE', 'Manage activities'),
    ('ACTIVITY_LOG_APPEND', 'Append activity logs'),
    ('HARVEST_READ', 'Read harvest facts'),
    ('HARVEST_MANAGE', 'Manage harvest facts'),
    ('INVENTORY_READ', 'Read inventory data'),
    ('INVENTORY_MANAGE', 'Manage inventory data'),
    ('INVENTORY_ASSIGNMENT_MANAGE', 'Manage warehouse assignments'),
    ('COST_READ', 'Read operating costs'),
    ('COST_MANAGE', 'Manage operating costs');

INSERT INTO roles (code, display_name) VALUES
    ('TENANT_ADMIN', 'Tenant administrator'),
    ('EXECUTIVE', 'Executive'),
    ('FARM_MANAGER', 'Farm manager'),
    ('INVENTORY_MANAGER', 'Inventory manager'),
    ('DATA_ANALYST', 'Data analyst'),
    ('FIELD_WORKER', 'Field worker'),
    ('SUPPLIER', 'Supplier');

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'TENANT_ADMIN', code
FROM permissions;

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('EXECUTIVE', 'FARM_READ'),
    ('EXECUTIVE', 'SEASON_READ'),
    ('EXECUTIVE', 'ACTIVITY_READ'),
    ('EXECUTIVE', 'HARVEST_READ'),
    ('EXECUTIVE', 'INVENTORY_READ'),
    ('EXECUTIVE', 'COST_READ'),
    ('FARM_MANAGER', 'FARM_READ'),
    ('FARM_MANAGER', 'FARM_MANAGE'),
    ('FARM_MANAGER', 'SEASON_READ'),
    ('FARM_MANAGER', 'SEASON_MANAGE'),
    ('FARM_MANAGER', 'WORKFORCE_PICKER_READ'),
    ('FARM_MANAGER', 'ACTIVITY_READ'),
    ('FARM_MANAGER', 'ACTIVITY_MANAGE'),
    ('FARM_MANAGER', 'ACTIVITY_LOG_APPEND'),
    ('FARM_MANAGER', 'HARVEST_READ'),
    ('FARM_MANAGER', 'HARVEST_MANAGE'),
    ('FARM_MANAGER', 'INVENTORY_READ'),
    ('FARM_MANAGER', 'COST_READ'),
    ('INVENTORY_MANAGER', 'INVENTORY_READ'),
    ('INVENTORY_MANAGER', 'INVENTORY_MANAGE'),
    ('DATA_ANALYST', 'FARM_READ'),
    ('DATA_ANALYST', 'SEASON_READ'),
    ('DATA_ANALYST', 'ACTIVITY_READ'),
    ('DATA_ANALYST', 'HARVEST_READ'),
    ('DATA_ANALYST', 'INVENTORY_READ'),
    ('DATA_ANALYST', 'COST_READ'),
    ('FIELD_WORKER', 'ACTIVITY_READ'),
    ('FIELD_WORKER', 'ACTIVITY_LOG_APPEND');
