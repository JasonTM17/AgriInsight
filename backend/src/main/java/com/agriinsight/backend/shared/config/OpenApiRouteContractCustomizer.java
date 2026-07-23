package com.agriinsight.backend.shared.config;

import com.agriinsight.backend.shared.api.SecuredRouteRegistry;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class OpenApiRouteContractCustomizer implements OpenApiCustomizer {

    private static final Set<String> SHARED_REQUEST_HEADERS = Set.of(
            "Idempotency-Key", "If-Match", OpenApiContractConfig.CORRELATION_HEADER);
    private final ObjectProvider<SecuredRouteRegistry> registryProvider;

    public OpenApiRouteContractCustomizer(ObjectProvider<SecuredRouteRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    @Override
    public void customise(OpenAPI openApi) {
        SecuredRouteRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Paths documented = Objects.requireNonNullElseGet(openApi.getPaths(), Paths::new);
        Paths ordered = new Paths();
        for (SecuredRouteRegistry.Route route : registry.routes()) {
            PathItem source = documented.get(route.pattern());
            Operation operation = source == null ? null : source.readOperationsMap().get(httpMethod(route));
            if (operation == null) {
                throw new IllegalStateException("OpenAPI is missing secured route " + routeKey(route));
            }
            harden(openApi.getComponents(), operation, route);
            PathItem target = ordered.computeIfAbsent(route.pattern(), ignored -> new PathItem());
            target.operation(httpMethod(route), operation);
        }
        rejectExtraOperations(documented, registry.routes());
        openApi.setPaths(ordered);
        sortComponents(openApi.getComponents());
        if (openApi.getTags() != null) {
            openApi.setTags(openApi.getTags().stream()
                    .sorted(Comparator.comparing(Tag::getName, Comparator.nullsLast(String::compareTo)))
                    .toList());
        }
    }

    private static void harden(
            Components components,
            Operation operation,
            SecuredRouteRegistry.Route route) {
        operation.setOperationId(operationId(route));
        operation.setSecurity(List.of(new SecurityRequirement().addList(OpenApiContractConfig.BEARER_AUTH)));
        operation.addExtension("x-required-authority", route.requiredAuthority().orElse("AUTHENTICATED"));
        replaceSharedRequestHeaders(operation);
        operation.getResponses()
                .addApiResponse("400", new ApiResponse().$ref("#/components/responses/BadRequest"))
                .addApiResponse("401", new ApiResponse().$ref("#/components/responses/Unauthorized"))
                .addApiResponse("403", new ApiResponse().$ref("#/components/responses/Forbidden"));
        if (route.method() != HttpMethod.GET) {
            operation.getResponses()
                    .addApiResponse("409", new ApiResponse().$ref("#/components/responses/Conflict"));
        }
        if (route.method() == HttpMethod.GET && returnsVersionedResource(components, operation)) {
            operation.getResponses().get("200").addHeaderObject(
                    "ETag", new Header().$ref("#/components/headers/ETag"));
        }
        operation.getResponses().values().forEach(OpenApiRouteContractCustomizer::addCorrelationHeader);
    }

    private static void replaceSharedRequestHeaders(Operation operation) {
        List<Parameter> parameters = Objects.requireNonNullElseGet(
                operation.getParameters(), List::of);
        List<Parameter> normalized = new ArrayList<>(parameters.size() + 1);
        boolean hasCorrelation = false;
        for (Parameter parameter : parameters) {
            if ("header".equals(parameter.getIn()) && SHARED_REQUEST_HEADERS.contains(parameter.getName())) {
                normalized.add(new Parameter().$ref("#/components/parameters/" + parameter.getName()));
                hasCorrelation |= OpenApiContractConfig.CORRELATION_HEADER.equals(parameter.getName());
            } else {
                normalized.add(parameter);
            }
        }
        if (!hasCorrelation) {
            normalized.add(new Parameter().$ref(
                    "#/components/parameters/" + OpenApiContractConfig.CORRELATION_HEADER));
        }
        operation.setParameters(normalized);
    }

    private static boolean returnsVersionedResource(
            Components components,
            Operation operation) {
        ApiResponse success = operation.getResponses().get("200");
        if (success == null || success.getContent() == null || components == null) {
            return false;
        }
        return success.getContent().values().stream()
                .map(mediaType -> resolveSchema(components, mediaType.getSchema()))
                .filter(Objects::nonNull)
                .anyMatch(schema -> schema.getProperties() != null
                        && schema.getProperties().containsKey("version"));
    }

    private static Schema<?> resolveSchema(Components components, Schema<?> schema) {
        if (schema == null || schema.get$ref() == null) {
            return schema;
        }
        String prefix = "#/components/schemas/";
        if (!schema.get$ref().startsWith(prefix) || components.getSchemas() == null) {
            return null;
        }
        return components.getSchemas().get(schema.get$ref().substring(prefix.length()));
    }

    private static void addCorrelationHeader(ApiResponse response) {
        if (response.getHeaders() == null || !response.getHeaders().containsKey(OpenApiContractConfig.CORRELATION_HEADER)) {
            response.addHeaderObject(OpenApiContractConfig.CORRELATION_HEADER, new Header().$ref(
                    "#/components/headers/" + OpenApiContractConfig.CORRELATION_HEADER));
        }
    }

    private static void rejectExtraOperations(Paths documented, List<SecuredRouteRegistry.Route> routes) {
        Set<String> expected = routes.stream().map(OpenApiRouteContractCustomizer::routeKey).collect(
                java.util.stream.Collectors.toUnmodifiableSet());
        documented.forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            String key = method.name() + " " + path;
            if (!expected.contains(key)) {
                throw new IllegalStateException("OpenAPI contains unregistered route " + key);
            }
        }));
    }

    private static void sortComponents(Components components) {
        if (components == null) {
            return;
        }
        components.setSchemas(sorted(components.getSchemas()));
        components.setResponses(sorted(components.getResponses()));
        components.setParameters(sorted(components.getParameters()));
        components.setHeaders(sorted(components.getHeaders()));
        components.setSecuritySchemes(sorted(components.getSecuritySchemes()));
    }

    private static <T> Map<String, T> sorted(Map<String, T> values) {
        return values == null ? null : new LinkedHashMap<>(new TreeMap<>(values));
    }

    private static PathItem.HttpMethod httpMethod(SecuredRouteRegistry.Route route) {
        return PathItem.HttpMethod.valueOf(route.method().name());
    }

    private static String routeKey(SecuredRouteRegistry.Route route) {
        return route.method().name() + " " + route.pattern();
    }

    private static String operationId(SecuredRouteRegistry.Route route) {
        String raw = route.method().name().toLowerCase(Locale.ROOT) + "_" + route.pattern();
        return raw.replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
