package com.agriinsight.backend.shared.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public final class CanonicalCommandBody {

    private CanonicalCommandBody() {
    }

    public static String of(Map<String, ?> fields) {
        Objects.requireNonNull(fields, "fields are required");
        StringBuilder encoded = new StringBuilder("body-v1;");
        new TreeMap<>(fields).forEach((name, value) -> {
            requireName(name);
            append(encoded, name);
            appendValue(encoded, value);
        });
        return encoded.toString();
    }

    private static void appendValue(StringBuilder encoded, Object value) {
        if (value == null) {
            encoded.append('N');
            append(encoded, "");
            return;
        }
        if (value instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
                encoded.append('N');
                append(encoded, "");
            } else {
                appendValue(encoded, optional.orElseThrow());
            }
            return;
        }
        if (value instanceof String string) {
            encoded.append('S');
            append(encoded, string);
            return;
        }
        if (value instanceof Number number) {
            encoded.append('D');
            append(encoded, number.toString());
            return;
        }
        if (value instanceof Boolean bool) {
            encoded.append('B');
            append(encoded, bool.toString());
            return;
        }
        if (value instanceof Enum<?> enumeration) {
            encoded.append('E');
            append(encoded, enumeration.name());
            return;
        }
        if (value instanceof UUID uuid) {
            encoded.append('U');
            append(encoded, uuid.toString());
            return;
        }
        throw new IllegalArgumentException("Unsupported canonical command value type: "
                + value.getClass().getName());
    }

    private static void append(StringBuilder encoded, String value) {
        encoded.append(value.length()).append(':').append(value).append(';');
    }

    private static void requireName(String name) {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Canonical body field name has an invalid format");
        }
    }
}
