package com.agriinsight.backend.identity.application;

import java.util.Objects;

public record ExternalIdentityClaims(
        String issuer,
        String subject,
        String displayName,
        String email,
        String assurance) {

    private static final int MAX_ISSUER_LENGTH = 2048;
    private static final int MAX_SUBJECT_LENGTH = 512;

    public ExternalIdentityClaims {
        issuer = requiredExact(issuer, "issuer", MAX_ISSUER_LENGTH);
        subject = requiredExact(subject, "subject", MAX_SUBJECT_LENGTH);
        displayName = optionalBounded(displayName, "displayName", 200);
        email = optionalBounded(email, "email", 320);
        assurance = optionalBounded(assurance, "assurance", 128);
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

    private static String optionalBounded(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
