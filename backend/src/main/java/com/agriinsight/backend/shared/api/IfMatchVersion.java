package com.agriinsight.backend.shared.api;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record IfMatchVersion(long value) {

    private static final Pattern ETAG = Pattern.compile("\\\"([0-9]{1,19})\\\"");

    public IfMatchVersion {
        if (value < 0) {
            throw new IllegalArgumentException("If-Match version must not be negative");
        }
    }

    public static IfMatchVersion parse(String rawHeader) {
        String value = Objects.requireNonNull(rawHeader, "If-Match is required");
        Matcher matcher = ETAG.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("If-Match must be a quoted non-negative version");
        }
        try {
            return new IfMatchVersion(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match version is too large", exception);
        }
    }

    public String canonicalHeaderValue() {
        return "\"" + value + "\"";
    }
}
