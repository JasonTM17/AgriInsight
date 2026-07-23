package com.agriinsight.backend.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiContractConfig {

    static final String BEARER_AUTH = "bearerAuth";
    static final String PROBLEM_SCHEMA = "AgriInsightProblemDetail";
    static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Bean
    OpenAPI agriInsightOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSchemas(PROBLEM_SCHEMA, problemSchema())
                .addHeaders(CORRELATION_HEADER, stringHeader("Request correlation identifier"))
                .addHeaders("ETag", stringHeader("Strong entity version tag"))
                .addHeaders("WWW-Authenticate", stringHeader("Bearer authentication challenge"))
                .addParameters("Idempotency-Key", requestHeader(
                        "Idempotency-Key", "Unique command replay key", true))
                .addParameters("If-Match", requestHeader(
                        "If-Match", "Strong entity version precondition", true))
                .addParameters(CORRELATION_HEADER, requestHeader(
                        CORRELATION_HEADER, "Optional caller correlation identifier", false));

        components
                .addResponses("BadRequest", problemResponse("The request is malformed or invalid", false))
                .addResponses("Unauthorized", problemResponse("Authentication is required", true))
                .addResponses("Forbidden", problemResponse("The authenticated principal is not authorized", false))
                .addResponses("Conflict", problemResponse("The request conflicts with current resource state", false));

        return new OpenAPI()
                .info(new Info()
                        .title("AgriInsight Backend API")
                        .version("1.0.0")
                        .description("Secured operational API for the AgriInsight web BFF."))
                .servers(List.of(new Server().url("/").description("Current deployment")))
                .components(components);
    }

    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema().format("int32"))
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("correlationId", new StringSchema())
                .addProperty("fieldErrors", new ObjectSchema())
                .addProperty("expectedVersion", new IntegerSchema().format("int64"))
                .addProperty("currentVersion", new IntegerSchema().format("int64"));
    }

    private static Header stringHeader(String description) {
        return new Header().description(description).schema(new StringSchema());
    }

    private static Parameter requestHeader(String name, String description, boolean required) {
        return new Parameter()
                .name(name)
                .in("header")
                .description(description)
                .required(required)
                .schema(new StringSchema());
    }

    private static ApiResponse problemResponse(String description, boolean challenge) {
        ApiResponse response = new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/problem+json", new MediaType()
                        .schema(new ObjectSchema().$ref("#/components/schemas/" + PROBLEM_SCHEMA))))
                .addHeaderObject(CORRELATION_HEADER, new Header().$ref(
                        "#/components/headers/" + CORRELATION_HEADER));
        if (challenge) {
            response.addHeaderObject("WWW-Authenticate", new Header().$ref(
                    "#/components/headers/WWW-Authenticate"));
        }
        return response;
    }
}
