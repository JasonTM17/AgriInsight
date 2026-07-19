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
@Table(name = "external_identities")
public class ExternalIdentity extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_profile_id", nullable = false, updatable = false)
    private UUID userProfileId;

    @Column(nullable = false, length = 2048, updatable = false)
    private String issuer;

    @Column(nullable = false, length = 512, updatable = false)
    private String subject;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;

    protected ExternalIdentity() {
    }

    public ExternalIdentity(UUID id, UUID tenantId, UUID userProfileId, String issuer, String subject) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.userProfileId = Objects.requireNonNull(userProfileId, "userProfileId is required");
        this.issuer = requiredExact(issuer, "issuer", 2048);
        this.subject = requiredExact(subject, "subject", 512);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserProfileId() {
        return userProfileId;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
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

    private static String requiredExact(String value, String fieldName, int maxLength) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
