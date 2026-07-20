package com.agriinsight.backend.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class CanonicalCommandMaterial {

    private static final Pattern HTTP_METHOD = Pattern.compile("[A-Z]{3,8}");
    private static final Pattern ROUTE_TEMPLATE = Pattern.compile("/api/v1/[A-Za-z0-9_{}./-]+");
    private static final Pattern COMPONENT_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,63}");
    private static final Set<String> CREDENTIAL_HEADERS = Set.of(
            "authorization",
            "cookie",
            "idempotency-key",
            "proxy-authorization",
            "set-cookie",
            "x-api-key");

    private final String httpMethod;
    private final String routeTemplate;
    private final Map<String, String> pathVariables;
    private final Map<String, List<String>> queryValues;
    private final String canonicalBody;
    private final Map<String, String> semanticHeaders;

    public CanonicalCommandMaterial(
            String httpMethod,
            String routeTemplate,
            Map<String, String> pathVariables,
            Map<String, List<String>> queryValues,
            String canonicalBody,
            Map<String, String> semanticHeaders) {
        this.httpMethod = normalizeMethod(httpMethod);
        this.routeTemplate = requireRouteTemplate(routeTemplate);
        this.pathVariables = copyScalarMap(pathVariables, "path variable", false);
        this.queryValues = copyListMap(queryValues);
        this.canonicalBody = Objects.requireNonNull(canonicalBody, "canonicalBody is required");
        this.semanticHeaders = copyScalarMap(semanticHeaders, "semantic header", true);
    }

    public String httpMethod() {
        return httpMethod;
    }

    public String routeTemplate() {
        return routeTemplate;
    }

    public Map<String, String> pathVariables() {
        return pathVariables;
    }

    public Map<String, List<String>> queryValues() {
        return queryValues;
    }

    public String canonicalBody() {
        return canonicalBody;
    }

    public Map<String, String> semanticHeaders() {
        return semanticHeaders;
    }

    private static String normalizeMethod(String value) {
        String method = Objects.requireNonNull(value, "httpMethod is required")
                .strip()
                .toUpperCase(Locale.ROOT);
        if (!HTTP_METHOD.matcher(method).matches()) {
            throw new IllegalArgumentException("httpMethod has an invalid format");
        }
        return method;
    }

    private static String requireRouteTemplate(String value) {
        String route = Objects.requireNonNull(value, "routeTemplate is required").strip();
        if (route.length() > 240 || !ROUTE_TEMPLATE.matcher(route).matches()) {
            throw new IllegalArgumentException("routeTemplate must be a registered /api/v1 route template");
        }
        return route;
    }

    private static Map<String, String> copyScalarMap(
            Map<String, String> source,
            String fieldName,
            boolean normalizeHeaderNames) {
        Objects.requireNonNull(source, fieldName + "s are required");
        Map<String, String> copy = new TreeMap<>();
        source.forEach((rawName, rawValue) -> {
            String name = requireComponentName(rawName, fieldName);
            if (normalizeHeaderNames) {
                name = name.toLowerCase(Locale.ROOT);
                if (CREDENTIAL_HEADERS.contains(name)) {
                    throw new IllegalArgumentException(fieldName + " must not contain credentials or idempotency keys");
                }
            }
            String value = Objects.requireNonNull(rawValue, fieldName + " value is required");
            if (copy.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException(fieldName + " names must be unique after normalization");
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, List<String>> copyListMap(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "query values are required");
        Map<String, List<String>> copy = new TreeMap<>();
        source.forEach((rawName, rawValues) -> {
            String name = requireComponentName(rawName, "query value");
            List<String> values = new ArrayList<>(Objects.requireNonNull(
                    rawValues,
                    "query value list is required"));
            values.forEach(value -> Objects.requireNonNull(value, "query value is required"));
            if (copy.putIfAbsent(name, List.copyOf(values)) != null) {
                throw new IllegalArgumentException("query value names must be unique");
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static String requireComponentName(String value, String fieldName) {
        String name = Objects.requireNonNull(value, fieldName + " name is required");
        if (!COMPONENT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(fieldName + " name has an invalid format");
        }
        return name;
    }
}
