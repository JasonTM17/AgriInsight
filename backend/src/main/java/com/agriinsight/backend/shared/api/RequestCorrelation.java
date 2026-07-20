package com.agriinsight.backend.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestCorrelation {

    public static final String HEADER = "X-Correlation-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private RequestCorrelation() {
    }

    public static String resolve(HttpServletRequest request) {
        HttpServletRequest requiredRequest = Objects.requireNonNull(request, "request is required");
        Object attribute = requiredRequest.getAttribute(HEADER);
        if (attribute instanceof String value && isSafe(value)) {
            return value;
        }
        String supplied = requiredRequest.getHeader(HEADER);
        String correlationId = isSafe(supplied) ? supplied : UUID.randomUUID().toString();
        requiredRequest.setAttribute(HEADER, correlationId);
        return correlationId;
    }

    private static boolean isSafe(String value) {
        return value != null && SAFE_ID.matcher(value).matches();
    }
}
