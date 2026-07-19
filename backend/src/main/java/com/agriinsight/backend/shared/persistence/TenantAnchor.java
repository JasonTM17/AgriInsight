package com.agriinsight.backend.shared.persistence;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tenants")
public class TenantAnchor extends AuditableEntity {

    private static final Pattern BUSINESS_CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");

    @Id
    private UUID id;

    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;

    protected TenantAnchor() {
    }

    public TenantAnchor(UUID id, String code, String displayName) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.code = canonicalCode(code);
        this.displayName = displayName(displayName);
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    private static String canonicalCode(String value) {
        String canonical = requiredText(value, "code").toUpperCase(Locale.ROOT);
        requireMaxLength(canonical, "code", 64);
        if (!BUSINESS_CODE.matcher(canonical).matches()) {
            throw new IllegalArgumentException("code must use ASCII letters, digits, dot, underscore, or hyphen");
        }
        return canonical;
    }

    private static String displayName(String value) {
        return requireMaxLength(requiredText(value, "displayName"), "displayName", 200);
    }

    private static String requiredText(String value, String fieldName) {
        String normalized = stripCanonicalWhitespace(Objects.requireNonNull(value, fieldName + " is required"));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String stripCanonicalWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isCanonicalWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isCanonicalWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isCanonicalWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x0085;
    }

    private static String requireMaxLength(String value, String fieldName, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
