package com.agriinsight.backend.shared.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class IdempotencyKey {

    private final String digest;

    private IdempotencyKey(String digest) {
        this.digest = digest;
    }

    public static IdempotencyKey parse(String rawValue) {
        String value = Objects.requireNonNull(rawValue, "Idempotency-Key is required");
        if (!value.matches("[!-~]{1,200}")) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 200 visible ASCII characters");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            return new IdempotencyKey(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public String digest() {
        return digest;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof IdempotencyKey other && digest.equals(other.digest);
    }

    @Override
    public int hashCode() {
        return digest.hashCode();
    }

    @Override
    public String toString() {
        return "IdempotencyKey[redacted]";
    }
}
