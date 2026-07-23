package com.agriinsight.backend.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.IdentityBootstrapPort;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
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
class OpenApiDeterminismTest {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");

    @Autowired private MockMvc mockMvc;
    @Autowired private SecuredRouteRegistry routeRegistry;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private IdentityBootstrapPort bootstrapPort;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void emitsStableBytesAndExactlyTheSecuredRouteInventory() throws Exception {
        byte[] first = fetch();
        byte[] second = fetch();

        assertThat(first).isEqualTo(second);
        String document = new String(first, StandardCharsets.UTF_8);
        assertThat(document).doesNotContain("127.0.0.1").doesNotContain("localhost");

        JsonNode root = mapper.readTree(first);
        Set<String> actual = new TreeSet<>();
        Set<String> operationIds = new HashSet<>();
        for (var path : root.get("paths").properties()) {
            for (var operation : path.getValue().properties()) {
                if (HTTP_METHODS.contains(operation.getKey())) {
                    actual.add(operation.getKey().toUpperCase() + " " + path.getKey());
                    operationIds.add(operation.getValue().get("operationId").asString());
                }
            }
        }
        Set<String> expected = new TreeSet<>();
        routeRegistry.routes().forEach(route -> expected.add(route.method().name() + " " + route.pattern()));

        assertThat(actual).isEqualTo(expected);
        assertThat(operationIds).hasSize(expected.size());
        assertThat(root.get("servers").get(0).get("url").asString()).isEqualTo("/");
    }

    private byte[] fetch() throws Exception {
        return mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }
}
