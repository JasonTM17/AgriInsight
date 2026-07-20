package com.agriinsight.backend.shared.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CanonicalCommandHasher {

    public static final short CURRENT_SCHEMA_VERSION = 1;
    private static final String PROTOCOL = "AgriInsight.CommandHash";

    public Fingerprint fingerprint(short schemaVersion, CanonicalCommandMaterial command) {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(command, "command is required");

        MessageDigest digest = sha256();
        updateString(digest, PROTOCOL);
        updateInt(digest, schemaVersion);
        updateString(digest, command.httpMethod());
        updateString(digest, command.routeTemplate());
        updateScalarMap(digest, command.pathVariables());
        updateListMap(digest, command.queryValues());
        updateString(digest, command.canonicalBody());
        updateScalarMap(digest, command.semanticHeaders());
        return new Fingerprint(
                schemaVersion,
                command.httpMethod(),
                command.routeTemplate(),
                HexFormat.of().formatHex(digest.digest()));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void updateScalarMap(MessageDigest digest, Map<String, String> values) {
        updateInt(digest, values.size());
        values.forEach((name, value) -> {
            updateString(digest, name);
            updateString(digest, value);
        });
    }

    private static void updateListMap(MessageDigest digest, Map<String, List<String>> values) {
        updateInt(digest, values.size());
        values.forEach((name, items) -> {
            updateString(digest, name);
            updateInt(digest, items.size());
            items.forEach(item -> updateString(digest, item));
        });
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, encoded.length);
        digest.update(encoded);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    public record Fingerprint(
            short schemaVersion,
            String httpMethod,
            String routeTemplate,
            String commandHash) {

        public Fingerprint {
            if (schemaVersion <= 0) {
                throw new IllegalArgumentException("schemaVersion must be positive");
            }
            Objects.requireNonNull(httpMethod, "httpMethod is required");
            Objects.requireNonNull(routeTemplate, "routeTemplate is required");
            Objects.requireNonNull(commandHash, "commandHash is required");
            if (!commandHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("commandHash must be a lowercase SHA-256 digest");
            }
        }
    }
}
