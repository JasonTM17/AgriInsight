package com.agriinsight.backend.realtime.infrastructure;

/** SQL contracts that prove the alert worker can use only its required metadata topology. */
final class RealtimeWorkerRoleVerificationSql {

    static final String REQUIRED_SCHEMA_VERSION = "27";
    static final String REQUIRED_GRANTS_MIGRATION = "R__tenant_rls_helpers_and_grants.sql";

    static final String EXPECTED_SCHEMA_VERSION_QUERY = """
            SELECT EXISTS (
                SELECT 1
                  FROM public.flyway_schema_history
                 WHERE version = ?
                   AND success = TRUE
            )
            """;

    static final String REQUIRED_GRANTS_MIGRATION_QUERY = """
            SELECT COALESCE((
                SELECT success
                  FROM public.flyway_schema_history
                 WHERE version IS NULL
                   AND script = ?
                 ORDER BY installed_rank DESC
                 LIMIT 1
            ), FALSE)
            """;

    static final String ROLE_TOPOLOGY_QUERY = """
            WITH allowed_metadata_column(relation_name, column_name) AS (
                VALUES
                    ('outbox_events'::NAME, 'id'::NAME),
                    ('outbox_events'::NAME, 'tenant_id'::NAME),
                    ('outbox_events'::NAME, 'occurred_at'::NAME),
                    ('outbox_events'::NAME, 'status'::NAME),
                    ('outbox_events'::NAME, 'published_at'::NAME),
                    ('realtime_event_receipts'::NAME, 'event_id'::NAME),
                    ('realtime_event_receipts'::NAME, 'tenant_id'::NAME),
                    ('tenants'::NAME, 'id'::NAME),
                    ('flyway_schema_history'::NAME, 'installed_rank'::NAME),
                    ('flyway_schema_history'::NAME, 'script'::NAME),
                    ('flyway_schema_history'::NAME, 'version'::NAME),
                    ('flyway_schema_history'::NAME, 'success'::NAME)
            ),
            metadata_relation(relation_name) AS (
                VALUES
                    ('outbox_events'::NAME),
                    ('realtime_event_receipts'::NAME),
                    ('tenants'::NAME),
                    ('flyway_schema_history'::NAME)
            ),
            worker_state_table(relation_name, select_allowed, insert_allowed, delete_allowed) AS (
                VALUES
                    ('realtime_operational_alerts'::NAME, TRUE, TRUE, FALSE),
                    ('realtime_operational_alert_scan_cursors'::NAME, TRUE, TRUE, TRUE)
            ),
            allowed_worker_state_update_column(relation_name, column_name) AS (
                VALUES
                    ('realtime_operational_alerts'::NAME, 'severity'::NAME),
                    ('realtime_operational_alerts'::NAME, 'state'::NAME),
                    ('realtime_operational_alerts'::NAME, 'source_event_id'::NAME),
                    ('realtime_operational_alerts'::NAME, 'source_occurred_at'::NAME),
                    ('realtime_operational_alerts'::NAME, 'last_observed_at'::NAME),
                    ('realtime_operational_alerts'::NAME, 'resolved_at'::NAME),
                    ('realtime_operational_alerts'::NAME, 'clean_since'::NAME),
                    ('realtime_operational_alerts'::NAME, 'clean_scan_count'::NAME),
                    ('realtime_operational_alerts'::NAME, 'last_evaluated_at'::NAME),
                    ('realtime_operational_alerts'::NAME, 'version'::NAME),
                    ('realtime_operational_alert_scan_cursors'::NAME, 'cursor_tenant_id'::NAME),
                    ('realtime_operational_alert_scan_cursors'::NAME, 'cursor_ordered_at'::NAME),
                    ('realtime_operational_alert_scan_cursors'::NAME, 'cursor_ordered_id'::NAME),
                    ('realtime_operational_alert_scan_cursors'::NAME, 'cycle_started_at'::NAME),
                    ('realtime_operational_alert_scan_cursors'::NAME, 'updated_at'::NAME)
            ),
            required_worker_policy(
                policy_name, relation_name, policy_command, using_expression, check_expression
            ) AS (
                VALUES
                    ('alert_worker_tenants_select'::NAME, 'tenants'::NAME, 'r', 'true', NULL),
                    ('alert_worker_outbox_read'::NAME, 'outbox_events'::NAME, 'r', 'true', NULL),
                    ('alert_worker_realtime_event_receipts_read'::NAME,
                     'realtime_event_receipts'::NAME, 'r', 'true', NULL),
                    ('alert_worker_realtime_operational_alerts_select'::NAME,
                     'realtime_operational_alerts'::NAME, 'r', 'true', NULL),
                    ('alert_worker_realtime_operational_alerts_insert'::NAME,
                     'realtime_operational_alerts'::NAME, 'a', NULL, 'true'),
                    ('alert_worker_realtime_operational_alerts_update'::NAME,
                     'realtime_operational_alerts'::NAME, 'w', 'true', 'true'),
                    ('alert_worker_realtime_operational_alert_scan_cursors_select'::NAME,
                     'realtime_operational_alert_scan_cursors'::NAME, 'r', 'true', NULL),
                    ('alert_worker_realtime_operational_alert_scan_cursors_insert'::NAME,
                     'realtime_operational_alert_scan_cursors'::NAME, 'a', NULL, 'true'),
                    ('alert_worker_realtime_operational_alert_scan_cursors_update'::NAME,
                     'realtime_operational_alert_scan_cursors'::NAME, 'w', 'true', 'true'),
                    ('alert_worker_realtime_operational_alert_scan_cursors_delete'::NAME,
                     'realtime_operational_alert_scan_cursors'::NAME, 'd', 'true', NULL)
            )
            SELECT current_user = CAST(? AS NAME)
               AND session_user = current_user
               AND has_schema_privilege(current_user, 'public', 'USAGE')
               AND EXISTS (
                    SELECT 1
                      FROM pg_catalog.pg_roles AS worker_role
                     WHERE worker_role.rolname = current_user
                       AND worker_role.rolcanlogin
                       AND NOT worker_role.rolsuper
                       AND NOT worker_role.rolinherit
                       AND NOT worker_role.rolcreaterole
                       AND NOT worker_role.rolcreatedb
                       AND NOT worker_role.rolreplication
                       AND NOT worker_role.rolbypassrls
               )
               AND NOT EXISTS (
                    SELECT 1
                      FROM pg_catalog.pg_auth_members AS membership
                      JOIN pg_catalog.pg_roles AS member_role
                        ON member_role.oid = membership.member
                     WHERE member_role.rolname = current_user
               )
               AND NOT EXISTS (
                    SELECT 1
                      FROM pg_catalog.pg_class AS relation
                      JOIN pg_catalog.pg_namespace AS namespace
                        ON namespace.oid = relation.relnamespace
                     WHERE namespace.nspname = 'public'
                       AND relation.relkind IN ('r', 'p', 'v', 'm', 'f')
                       AND relation.relname <> ALL (ARRAY[
                            'outbox_events'::NAME,
                            'realtime_event_receipts'::NAME,
                            'tenants'::NAME,
                            'flyway_schema_history'::NAME,
                            'realtime_operational_alerts'::NAME,
                            'realtime_operational_alert_scan_cursors'::NAME
                       ])
                       AND (
                            has_table_privilege(current_user, relation.oid, 'SELECT')
                            OR has_table_privilege(current_user, relation.oid, 'INSERT')
                            OR has_table_privilege(current_user, relation.oid, 'UPDATE')
                            OR has_table_privilege(current_user, relation.oid, 'DELETE')
                            OR has_table_privilege(current_user, relation.oid, 'TRUNCATE')
                            OR has_table_privilege(current_user, relation.oid, 'REFERENCES')
                            OR has_table_privilege(current_user, relation.oid, 'TRIGGER')
                            OR has_any_column_privilege(
                                current_user, relation.oid, 'SELECT, INSERT, UPDATE, REFERENCES')
                       )
               )
               AND (
                    SELECT COALESCE(bool_and(
                        NOT has_table_privilege(
                            current_user, 'public.' || metadata.relation_name, 'SELECT')
                        AND NOT has_table_privilege(
                            current_user, 'public.' || metadata.relation_name, 'INSERT')
                        AND NOT has_table_privilege(
                            current_user, 'public.' || metadata.relation_name, 'UPDATE')
                        AND NOT has_table_privilege(
                            current_user, 'public.' || metadata.relation_name, 'DELETE')
                        AND NOT has_table_privilege(
                            current_user, 'public.' || metadata.relation_name, 'TRUNCATE')
                        AND NOT has_table_privilege(
                            current_user, 'public.' || metadata.relation_name, 'REFERENCES')
                        AND NOT has_table_privilege(
                            current_user, 'public.' || metadata.relation_name, 'TRIGGER')
                        AND NOT has_any_column_privilege(
                            current_user,
                            'public.' || metadata.relation_name,
                            'INSERT, UPDATE, REFERENCES'
                        )
                    ), FALSE)
                    FROM metadata_relation AS metadata
               )
               AND (
                    SELECT COALESCE(bool_and(has_column_privilege(
                        current_user,
                        'public.' || required.relation_name,
                        required.column_name,
                        'SELECT'
                    )), FALSE)
                    FROM allowed_metadata_column AS required
               )
               AND NOT EXISTS (
                    SELECT 1
                      FROM pg_catalog.pg_class AS relation
                      JOIN pg_catalog.pg_namespace AS namespace
                        ON namespace.oid = relation.relnamespace
                      JOIN metadata_relation AS metadata
                        ON metadata.relation_name = relation.relname
                      JOIN pg_catalog.pg_attribute AS attribute_row
                        ON attribute_row.attrelid = relation.oid
                       AND attribute_row.attnum > 0
                       AND NOT attribute_row.attisdropped
                      LEFT JOIN allowed_metadata_column AS allowed
                        ON allowed.relation_name = relation.relname
                       AND allowed.column_name = attribute_row.attname
                     WHERE namespace.nspname = 'public'
                       AND relation.relkind IN ('r', 'p')
                       AND has_column_privilege(
                            current_user, relation.oid, attribute_row.attname, 'SELECT')
                       AND allowed.column_name IS NULL
               )
               AND (
                    SELECT COALESCE(bool_and(
                        has_table_privilege(
                            current_user, 'public.' || state_relation.relation_name, 'SELECT')
                            = state_relation.select_allowed
                        AND has_table_privilege(
                            current_user, 'public.' || state_relation.relation_name, 'INSERT')
                            = state_relation.insert_allowed
                        AND has_table_privilege(
                            current_user, 'public.' || state_relation.relation_name, 'DELETE')
                            = state_relation.delete_allowed
                        AND NOT has_table_privilege(
                            current_user, 'public.' || state_relation.relation_name, 'UPDATE')
                        AND NOT has_table_privilege(
                            current_user, 'public.' || state_relation.relation_name, 'TRUNCATE')
                        AND NOT has_table_privilege(
                            current_user, 'public.' || state_relation.relation_name, 'REFERENCES')
                        AND NOT has_table_privilege(
                            current_user, 'public.' || state_relation.relation_name, 'TRIGGER')
                    ), FALSE)
                    FROM worker_state_table AS state_relation
               )
               AND (
                    SELECT COALESCE(bool_and(has_column_privilege(
                        current_user,
                        'public.' || allowed.relation_name,
                        allowed.column_name,
                        'UPDATE'
                    )), FALSE)
                    FROM allowed_worker_state_update_column AS allowed
               )
               AND NOT EXISTS (
                    SELECT 1
                      FROM pg_catalog.pg_class AS relation
                      JOIN pg_catalog.pg_namespace AS namespace
                        ON namespace.oid = relation.relnamespace
                      JOIN worker_state_table AS state_relation
                        ON state_relation.relation_name = relation.relname
                      JOIN pg_catalog.pg_attribute AS attribute_row
                        ON attribute_row.attrelid = relation.oid
                       AND attribute_row.attnum > 0
                       AND NOT attribute_row.attisdropped
                      LEFT JOIN allowed_worker_state_update_column AS allowed
                        ON allowed.relation_name = relation.relname
                       AND allowed.column_name = attribute_row.attname
                     WHERE namespace.nspname = 'public'
                       AND relation.relkind IN ('r', 'p')
                       AND (
                            has_column_privilege(
                                current_user, relation.oid, attribute_row.attname, 'UPDATE')
                            OR has_column_privilege(
                                current_user, relation.oid, attribute_row.attname, 'REFERENCES')
                       )
                       AND allowed.column_name IS NULL
               )
               AND (
                    SELECT COALESCE(bool_and(EXISTS (
                        SELECT 1
                          FROM pg_catalog.pg_class AS relation
                          JOIN pg_catalog.pg_namespace AS namespace
                            ON namespace.oid = relation.relnamespace
                          JOIN pg_catalog.pg_policy AS policy
                            ON policy.polrelid = relation.oid
                         WHERE namespace.nspname = 'public'
                           AND relation.relname = required_policy.relation_name
                           AND relation.relkind IN ('r', 'p')
                           AND relation.relrowsecurity
                           AND relation.relforcerowsecurity
                           AND policy.polname = required_policy.policy_name
                           AND policy.polpermissive
                           AND policy.polcmd::TEXT = required_policy.policy_command
                           AND policy.polroles = ARRAY[
                                (SELECT worker_role.oid
                                   FROM pg_catalog.pg_roles AS worker_role
                                  WHERE worker_role.rolname = current_user)
                           ]::OID[]
                           AND COALESCE(
                                pg_catalog.pg_get_expr(policy.polqual, policy.polrelid), '')
                                = COALESCE(required_policy.using_expression, '')
                           AND COALESCE(
                                pg_catalog.pg_get_expr(policy.polwithcheck, policy.polrelid), '')
                                = COALESCE(required_policy.check_expression, '')
                    )), FALSE)
                    FROM required_worker_policy AS required_policy
               )
            """;

    private RealtimeWorkerRoleVerificationSql() {
    }
}
