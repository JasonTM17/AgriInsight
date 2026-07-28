package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fails startup unless the isolated alert worker has the required narrow topology and login. */
public final class RealtimeWorkerRoleVerifier {

    private static final String REQUIRED_LOGIN = "agriinsight_alert_worker";
    private static final String EXPECTED_SCHEMA_VERSION_QUERY = """
            SELECT EXISTS (
                SELECT 1
                  FROM public.flyway_schema_history
                 WHERE version = ?
                   AND success = TRUE
            )
            """;
    private static final String ROLE_TOPOLOGY_QUERY = """
            WITH sensitive_business_relation(relation_name) AS (
                VALUES
                    ('user_profiles'::NAME),
                    ('external_identities'::NAME),
                    ('roles'::NAME),
                    ('permissions'::NAME),
                    ('user_roles'::NAME),
                    ('role_permissions'::NAME),
                    ('api_command_records'::NAME),
                    ('tenant_audit_events'::NAME),
                    ('farms'::NAME),
                    ('crops'::NAME),
                    ('fields'::NAME),
                    ('seasons'::NAME),
                    ('employees'::NAME),
                    ('user_farm_assignments'::NAME),
                    ('activity_types'::NAME),
                    ('activities'::NAME),
                    ('activity_assignees'::NAME),
                    ('activity_logs'::NAME),
                    ('harvests'::NAME),
                    ('warehouses'::NAME),
                    ('materials'::NAME),
                    ('suppliers'::NAME),
                    ('user_warehouse_assignments'::NAME),
                    ('inventory_transactions'::NAME),
                    ('inventory_transaction_lot_allocations'::NAME),
                    ('stock_lots'::NAME),
                    ('stock_balances'::NAME),
                    ('cost_categories'::NAME),
                    ('operating_cost_entries'::NAME),
                    ('realtime_aggregate_progress'::NAME),
                    ('realtime_tenant_metrics'::NAME),
                    ('realtime_alert_acknowledgement_revisions'::NAME)
            ),
            worker_metadata_relation(relation_name) AS (
                VALUES
                    ('outbox_events'::NAME),
                    ('realtime_event_receipts'::NAME),
                    ('tenants'::NAME),
                    ('flyway_schema_history'::NAME)
            ),
            allowed_metadata_column(relation_name, column_name) AS (
                VALUES
                    ('outbox_events'::NAME, 'id'::NAME),
                    ('outbox_events'::NAME, 'tenant_id'::NAME),
                    ('outbox_events'::NAME, 'occurred_at'::NAME),
                    ('outbox_events'::NAME, 'status'::NAME),
                    ('outbox_events'::NAME, 'published_at'::NAME),
                    ('realtime_event_receipts'::NAME, 'event_id'::NAME),
                    ('realtime_event_receipts'::NAME, 'tenant_id'::NAME),
                    ('tenants'::NAME, 'id'::NAME),
                    ('flyway_schema_history'::NAME, 'version'::NAME),
                    ('flyway_schema_history'::NAME, 'success'::NAME)
            )
            SELECT current_user = CAST(? AS NAME)
               AND session_user = current_user
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
                     JOIN sensitive_business_relation AS sensitive_relation
                       ON sensitive_relation.relation_name = relation.relname
                    WHERE namespace.nspname = 'public'
                      AND relation.relkind IN ('r', 'p')
                      AND (
                          has_table_privilege(
                              current_user,
                              relation.oid,
                              'SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER')
                          OR has_any_column_privilege(
                              current_user, relation.oid, 'SELECT, INSERT, UPDATE, REFERENCES')
                      )
               )
               AND NOT EXISTS (
                   SELECT 1
                     FROM pg_catalog.pg_class AS relation
                     JOIN pg_catalog.pg_namespace AS namespace
                       ON namespace.oid = relation.relnamespace
                     JOIN worker_metadata_relation AS metadata_relation
                       ON metadata_relation.relation_name = relation.relname
                    WHERE namespace.nspname = 'public'
                      AND relation.relkind IN ('r', 'p')
                      AND (
                          has_table_privilege(
                              current_user,
                              relation.oid,
                              'INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER')
                          OR has_any_column_privilege(
                              current_user, relation.oid, 'INSERT, UPDATE, REFERENCES')
                      )
               )
               AND NOT EXISTS (
                   SELECT 1
                     FROM pg_catalog.pg_class AS relation
                     JOIN pg_catalog.pg_namespace AS namespace
                       ON namespace.oid = relation.relnamespace
                     JOIN worker_metadata_relation AS metadata_relation
                       ON metadata_relation.relation_name = relation.relname
                     JOIN pg_catalog.pg_attribute AS column
                       ON column.attrelid = relation.oid
                      AND column.attnum > 0
                      AND NOT column.attisdropped
                     LEFT JOIN allowed_metadata_column AS allowed_column
                       ON allowed_column.relation_name = relation.relname
                      AND allowed_column.column_name = column.attname
                    WHERE namespace.nspname = 'public'
                      AND relation.relkind IN ('r', 'p')
                      AND has_column_privilege(
                              current_user, relation.oid, column.attname, 'SELECT')
                      AND allowed_column.column_name IS NULL
               )
            """;
    private static final String SOURCE_EVIDENCE_READINESS_QUERY = """
            SELECT EXISTS (
                SELECT 1
                  FROM realtime_operational_alerts
                 WHERE (
                     source_occurred_at IS NULL
                     OR (policy_code = 'OUTBOX_PUBLISH_BACKLOG'
                         AND source_event_id IS NOT NULL)
                     OR (policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')
                         AND source_event_id IS NULL)
                 )
                 LIMIT 1
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RealtimeWorkerProperties workerProperties;
    private final RealtimeAlertWorkerProperties alertProperties;
    private final KafkaProperties kafkaProperties;
    private final String expectedSchemaVersion;

    public RealtimeWorkerRoleVerifier(
            JdbcTemplate jdbcTemplate,
            RealtimeWorkerProperties workerProperties,
            RealtimeAlertWorkerProperties alertProperties,
            KafkaProperties kafkaProperties,
            String expectedSchemaVersion) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.workerProperties = Objects.requireNonNull(workerProperties, "workerProperties is required");
        this.alertProperties = Objects.requireNonNull(alertProperties, "alertProperties is required");
        this.kafkaProperties = Objects.requireNonNull(kafkaProperties, "kafkaProperties is required");
        this.expectedSchemaVersion =
                Objects.requireNonNull(expectedSchemaVersion, "expectedSchemaVersion is required");
    }

    public void verify() {
        if (workerProperties.publisherEnabled() || workerProperties.consumerEnabled()) {
            throw new IllegalStateException("operational alert worker cannot enable the legacy realtime pipeline");
        }
        if (workerProperties.topic().equals(alertProperties.observerFailureTopic())
                || workerProperties.deadLetterTopic().equals(alertProperties.observerFailureTopic())) {
            throw new IllegalStateException(
                    "observer failure topic must differ from the primary and observed DLT topics");
        }
        String legacyConsumerGroup = kafkaProperties.getConsumer().getGroupId();
        if (legacyConsumerGroup == null || legacyConsumerGroup.isBlank()) {
            throw new IllegalStateException("legacy consumer group must be explicit for worker isolation");
        }
        if (legacyConsumerGroup.equals(alertProperties.observerGroupId())) {
            throw new IllegalStateException("DLT observer group must differ from the legacy consumer group");
        }
        verifyExpectedSchemaVersion();
        verifyDatabaseRole();
        if (hasInvalidSourceEvidence()) {
            throw new IllegalStateException(
                    "operational alert worker source evidence backfill is incomplete");
        }
    }

    private void verifyExpectedSchemaVersion() {
        try {
            Boolean expectedSchemaVersionInstalled = jdbcTemplate.queryForObject(
                    EXPECTED_SCHEMA_VERSION_QUERY, Boolean.class, expectedSchemaVersion);
            if (!Boolean.TRUE.equals(expectedSchemaVersionInstalled)) {
                throw new IllegalStateException(
                        "operational alert worker expected schema version is not installed");
            }
        } catch (DataAccessException exception) {
            throw new IllegalStateException("operational alert worker schema verification failed");
        }
    }

    private void verifyDatabaseRole() {
        Boolean verified = jdbcTemplate.queryForObject(
                ROLE_TOPOLOGY_QUERY, Boolean.class, REQUIRED_LOGIN);
        if (!Boolean.TRUE.equals(verified)) {
            throw new IllegalStateException("operational alert worker database role verification failed");
        }
    }

    private boolean hasInvalidSourceEvidence() {
        Boolean invalidSourceEvidence = jdbcTemplate.execute(
                (ConnectionCallback<Boolean>) connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(SOURCE_EVIDENCE_READINESS_QUERY)) {
                        statement.setQueryTimeout(
                                Math.toIntExact(alertProperties.maximumQueryDuration().toSeconds()));
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                return true;
                            }
                            return resultSet.getBoolean(1);
                        }
                    }
                });
        return !Boolean.FALSE.equals(invalidSourceEvidence);
    }
}
