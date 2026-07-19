package com.agriinsight.backend.identity.domain;

import com.agriinsight.backend.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(length = 320)
    private String email;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserProfile() {
    }

    public UserProfile(UUID id, UUID tenantId, String displayName, String email) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.displayName = requiredDisplayName(displayName);
        this.email = optionalEmail(email);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    public void deactivate() {
        active = false;
    }

    public void reactivate() {
        active = true;
    }

    private static String requiredDisplayName(String value) {
        String normalized = stripCanonicalWhitespace(
                Objects.requireNonNull(value, "displayName is required"));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("displayName must not exceed 200 characters");
        }
        return normalized;
    }

    private static String optionalEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = stripCanonicalWhitespace(value);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 320) {
            throw new IllegalArgumentException("email must not exceed 320 characters");
        }
        return normalized;
    }

    private static String stripCanonicalWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isCanonicalWhitespace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (start < end && isCanonicalWhitespace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean isCanonicalWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x0085;
    }
}
