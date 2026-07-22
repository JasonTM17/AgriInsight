package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record TenantAuditEvent(
        ScopeContext scope,
        Action action,
        TargetType targetType,
        Optional<UUID> targetId,
        Optional<String> targetReference,
        Optional<String> reasonCode,
        Optional<String> correlationId,
        Outcome outcome) {

    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,79}");
    private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    public TenantAuditEvent {
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(targetType, "targetType is required");
        targetId = Objects.requireNonNull(targetId, "targetId is required");
        targetReference = normalized(targetReference, "targetReference", 200);
        reasonCode = normalized(reasonCode, "reasonCode", 80);
        correlationId = normalized(correlationId, "correlationId", 128);
        Objects.requireNonNull(outcome, "outcome is required");
        reasonCode.ifPresent(value -> requirePattern(value, REASON_CODE, "reasonCode"));
        correlationId.ifPresent(value -> requirePattern(value, CORRELATION_ID, "correlationId"));
    }

    private static Optional<String> normalized(
            Optional<String> value,
            String fieldName,
            int maxLength) {
        Optional<String> required = Objects.requireNonNull(value, fieldName + " is required");
        return required.map(String::strip).map(normalized -> {
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            if (normalized.length() > maxLength) {
                throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
            }
            return normalized;
        });
    }

    private static void requirePattern(String value, Pattern pattern, String fieldName) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " has an invalid format");
        }
    }

    public enum Action {
        USER_CREATED,
        USER_DEACTIVATED,
        USER_REACTIVATED,
        EXTERNAL_IDENTITY_LINKED,
        EXTERNAL_IDENTITY_UNLINKED,
        ROLE_GRANTED,
        ROLE_REVOKED,
        FARM_CREATED,
        FARM_UPDATED,
        FARM_DEACTIVATED,
        FARM_REACTIVATED,
        FIELD_CREATED,
        FIELD_UPDATED,
        FIELD_DEACTIVATED,
        FIELD_REACTIVATED,
        CROP_CREATED,
        CROP_UPDATED,
        CROP_DEACTIVATED,
        CROP_REACTIVATED,
        SEASON_CREATED,
        SEASON_UPDATED,
        SEASON_TRANSITIONED,
        ACTIVITY_CREATED,
        ACTIVITY_UPDATED,
        ACTIVITY_TRANSITIONED,
        EMPLOYEE_CREATED,
        EMPLOYEE_UPDATED,
        EMPLOYEE_DEACTIVATED,
        EMPLOYEE_REACTIVATED,
        FARM_ASSIGNMENT_GRANTED,
        FARM_ASSIGNMENT_REVOKED,
        WAREHOUSE_CREATED,
        WAREHOUSE_UPDATED,
        WAREHOUSE_DEACTIVATED,
        WAREHOUSE_REACTIVATED,
        MATERIAL_CREATED,
        MATERIAL_UPDATED,
        MATERIAL_DEACTIVATED,
        MATERIAL_REACTIVATED,
        ACTIVITY_ASSIGNMENT_GRANTED,
        ACTIVITY_ASSIGNMENT_REVOKED,
        ACTIVITY_LOG_APPENDED,
        ACTIVITY_LOG_CORRECTED,
        HARVEST_POSTED,
        HARVEST_CORRECTED,
        AUTHORIZATION_DENIED,
        IDEMPOTENCY_CONFLICT
    }

    public enum TargetType {
        USER_PROFILE,
        EXTERNAL_IDENTITY,
        USER_ROLE,
        FARM,
        FIELD,
        CROP,
        SEASON,
        ACTIVITY,
        EMPLOYEE,
        FARM_ASSIGNMENT,
        WAREHOUSE,
        MATERIAL,
        ACTIVITY_ASSIGNMENT,
        ACTIVITY_LOG,
        HARVEST,
        API_COMMAND
    }

    public enum Outcome {
        SUCCEEDED,
        DENIED,
        CONFLICT
    }
}
