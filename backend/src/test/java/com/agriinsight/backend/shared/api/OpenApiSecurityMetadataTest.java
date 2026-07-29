package com.agriinsight.backend.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.IdentityBootstrapPort;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@IdentitySecurityContext
@ActiveProfiles({"test", "dev"})
class OpenApiSecurityMetadataTest {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private IdentityBootstrapPort bootstrapPort;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void documentsSharedSecurityErrorsAndConcurrencyHeaders() throws Exception {
        byte[] bytes = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        JsonNode root = mapper.readTree(bytes);

        JsonNode components = root.get("components");
        assertThat(components.get("securitySchemes").get("bearerAuth").get("scheme").asString())
                .isEqualTo("bearer");
        assertThat(components.get("schemas").get("AgriInsightProblemDetail")).isNotNull();
        assertThat(components.get("headers").propertyNames())
                .contains("ETag", "WWW-Authenticate", "X-Correlation-Id");
        assertThat(components.get("parameters").propertyNames())
                .contains("Idempotency-Key", "If-Match", "X-Correlation-Id");
        assertThat(components.get("responses").propertyNames())
                .contains("BadRequest", "Unauthorized", "Forbidden", "NotFound", "Conflict");
        assertThat(components.get("schemas").get("AgriInsightProblemDetail")
                .get("properties").get("expectedVersion").get("format").asString())
                .isEqualTo("int64");

        for (var path : root.get("paths").properties()) {
            for (var operationEntry : path.getValue().properties()) {
                if (!HTTP_METHODS.contains(operationEntry.getKey())) {
                    continue;
                }
                JsonNode operation = operationEntry.getValue();
                assertThat(operation.get("security").get(0).get("bearerAuth")).isNotNull();
                assertThat(operation.get("x-required-authority").asString()).isNotBlank();
                assertParameterReference(operation, "X-Correlation-Id");
                assertThat(operation.get("responses").get("400").get("$ref").asString())
                        .isEqualTo("#/components/responses/BadRequest");
                assertThat(operation.get("responses").get("401").get("$ref").asString())
                        .isEqualTo("#/components/responses/Unauthorized");
                assertThat(operation.get("responses").get("403").get("$ref").asString())
                        .isEqualTo("#/components/responses/Forbidden");
            }
        }

        assertHeaderReference(root, "/api/v1/farms", "post", "Idempotency-Key");
        assertHeaderReference(root, "/api/v1/farms/{id}", "patch", "If-Match");
        assertResponseHeaderReference(root, "/api/v1/farms/{id}", "get", "200", "ETag");
        assertThat(root.get("paths").get("/api/v1/farms").get("get")
                .get("responses").get("200").path("headers").has("ETag")).isFalse();
        assertThat(root.get("paths").get("/api/v1/farms").get("post")
                .get("responses").get("409").get("$ref").asString())
                .isEqualTo("#/components/responses/Conflict");
        JsonNode acknowledgementResponses = root.get("paths")
                .get("/api/v1/realtime/alerts/{id}/acknowledgements")
                .get("post")
                .get("responses");
        assertThat(acknowledgementResponses.get("200")).isNotNull();
        assertThat(acknowledgementResponses.get("404").get("$ref").asString())
                .isEqualTo("#/components/responses/NotFound");

        JsonNode schemas = components.get("schemas");
        assertThat(schemas.get("RealtimeOperationalAlertAcknowledgementRequest")
                .get("additionalProperties")
                .asBoolean())
                .isFalse();
        JsonNode feedSchema = schemas.get("RealtimeOperationalAlertFeedResponse");
        assertThat(stringSet(feedSchema.get("required")))
                .containsExactlyInAnyOrder("generatedAt", "hasMore", "items", "limit");
        assertThat(feedSchema.get("properties").get("items").get("maxItems").asInt())
                .isEqualTo(50);
        assertThat(feedSchema.get("properties").get("limit").get("minimum").asInt())
                .isEqualTo(50);
        assertThat(feedSchema.get("properties").get("limit").get("maximum").asInt())
                .isEqualTo(50);
        JsonNode alertSchema = schemas.get("RealtimeOperationalAlertResponse");
        assertThat(stringSet(alertSchema.get("required")))
                .containsExactlyInAnyOrder(
                        "acknowledged",
                        "acknowledgedAt",
                        "ageSeconds",
                        "evidence",
                        "id",
                        "lastEvaluatedAt",
                        "lastObservedAt",
                        "openedAt",
                        "policy",
                        "severity",
                        "source",
                        "sourceOccurredAt",
                        "state");
        assertThat(stringSet(alertSchema.get("properties").get("acknowledgedAt").get("type")))
                .containsExactlyInAnyOrder("string", "null");
        assertThat(stringSet(schemas.get("Evidence").get("properties").get("id").get("type")))
                .containsExactlyInAnyOrder("string", "null");
    }

    private static void assertHeaderReference(JsonNode root, String path, String method, String name) {
        assertParameterReference(root.get("paths").get(path).get(method), name);
    }

    private static void assertParameterReference(JsonNode operation, String name) {
        JsonNode parameters = operation.get("parameters");
        assertThat(parameters).isNotNull();
        boolean found = false;
        for (int index = 0; index < parameters.size(); index++) {
            if (("#/components/parameters/" + name)
                    .equals(parameters.get(index).path("$ref").asString())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    private static void assertResponseHeaderReference(
            JsonNode root,
            String path,
            String method,
            String status,
            String name) {
        assertThat(root.get("paths").get(path).get(method)
                .get("responses").get(status).get("headers").get(name)
                .get("$ref").asString())
                .isEqualTo("#/components/headers/" + name);
    }

    private static Set<String> stringSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }
}
