package com.agriinsight.backend.shared.persistence;

import com.agriinsight.backend.shared.application.ApiCommandRecordStore;
import com.agriinsight.backend.shared.domain.ApiCommandRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class PostgresApiCommandRecordStore implements ApiCommandRecordStore {

    private static final String RECORD_COLUMNS = """
            id, tenant_id, principal_id, http_method, route_template,
            idempotency_key_digest, canonical_schema_version, command_hash,
            state, response_status, target_resource_type, target_resource_id, target_version
            """;
    private static final RowMapper<ApiCommandRecord> RECORD_MAPPER = (result, rowNumber) ->
            new ApiCommandRecord(
                    result.getObject("id", UUID.class),
                    result.getObject("tenant_id", UUID.class),
                    result.getObject("principal_id", UUID.class),
                    result.getString("http_method"),
                    result.getString("route_template"),
                    result.getString("idempotency_key_digest"),
                    result.getShort("canonical_schema_version"),
                    result.getString("command_hash"),
                    ApiCommandRecord.State.valueOf(result.getString("state")),
                    Optional.ofNullable(result.getObject("response_status", Integer.class)),
                    Optional.ofNullable(result.getString("target_resource_type")),
                    Optional.ofNullable(result.getObject("target_resource_id", UUID.class)),
                    Optional.ofNullable(result.getObject("target_version", Long.class)));

    private final JdbcTemplate jdbcTemplate;

    public PostgresApiCommandRecordStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public Claim claim(ApiCommandRecord reservation) {
        Objects.requireNonNull(reservation, "reservation is required");
        if (reservation.state() != ApiCommandRecord.State.IN_PROGRESS) {
            throw new IllegalArgumentException("Only an in-progress command can be claimed");
        }
        int inserted = jdbcTemplate.update("""
                INSERT INTO api_command_records (
                    id, tenant_id, principal_id, http_method, route_template,
                    idempotency_key_digest, canonical_schema_version, command_hash, state
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS')
                ON CONFLICT (
                    tenant_id, principal_id, http_method, route_template, idempotency_key_digest
                ) DO NOTHING
                """,
                reservation.commandId(),
                reservation.tenantId(),
                reservation.principalId(),
                reservation.httpMethod(),
                reservation.routeTemplate(),
                reservation.idempotencyKeyDigest(),
                reservation.canonicalSchemaVersion(),
                reservation.commandHash());
        if (inserted == 1) {
            return Claim.claimed(reservation);
        }
        if (inserted != 0) {
            throw new IllegalStateException("Command claim wrote an unexpected number of rows");
        }
        return Claim.existing(findByBinding(reservation).orElseThrow(() ->
                new IllegalStateException("Conflicting command record is not visible under READ COMMITTED")));
    }

    @Override
    public ApiCommandRecord complete(ApiCommandRecord completedRecord) {
        Objects.requireNonNull(completedRecord, "completedRecord is required");
        if (completedRecord.state() != ApiCommandRecord.State.COMPLETED) {
            throw new IllegalArgumentException("Only a completed command can be persisted");
        }
        List<ApiCommandRecord> rows = jdbcTemplate.query("""
                UPDATE api_command_records
                   SET state = 'COMPLETED',
                       response_status = ?,
                       target_resource_type = ?,
                       target_resource_id = ?,
                       target_version = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                   AND tenant_id = ?
                   AND principal_id = ?
                   AND http_method = ?
                   AND route_template = ?
                   AND idempotency_key_digest = ?
                   AND canonical_schema_version = ?
                   AND command_hash = ?
                   AND state = 'IN_PROGRESS'
                RETURNING
                """ + RECORD_COLUMNS,
                RECORD_MAPPER,
                completedRecord.responseStatus().orElseThrow(),
                completedRecord.targetResourceType().orElseThrow(),
                completedRecord.targetResourceId().orElseThrow(),
                completedRecord.targetVersion().orElseThrow(),
                completedRecord.commandId(),
                completedRecord.tenantId(),
                completedRecord.principalId(),
                completedRecord.httpMethod(),
                completedRecord.routeTemplate(),
                completedRecord.idempotencyKeyDigest(),
                completedRecord.canonicalSchemaVersion(),
                completedRecord.commandHash());
        return exactlyOne(rows, "Command completion did not update exactly one reservation");
    }

    private Optional<ApiCommandRecord> findByBinding(ApiCommandRecord record) {
        List<ApiCommandRecord> rows = jdbcTemplate.query(
                "SELECT " + RECORD_COLUMNS + " FROM api_command_records"
                        + " WHERE tenant_id = ? AND principal_id = ? AND http_method = ?"
                        + " AND route_template = ? AND idempotency_key_digest = ?",
                RECORD_MAPPER,
                record.tenantId(),
                record.principalId(),
                record.httpMethod(),
                record.routeTemplate(),
                record.idempotencyKeyDigest());
        if (rows.size() > 1) {
            throw new IllegalStateException("Command binding query returned more than one row");
        }
        return rows.stream().findFirst();
    }

    private ApiCommandRecord exactlyOne(List<ApiCommandRecord> rows, String message) {
        if (rows.size() != 1) {
            throw new IllegalStateException(message);
        }
        return rows.getFirst();
    }
}
