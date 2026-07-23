package com.agriinsight.backend.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.IdentityBootstrapPort;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
@ActiveProfiles({"test", "dev"})
class OpenApiArtifactExportTest {

    private static final Path SOURCE = Path.of(
            "src", "main", "resources", "contracts", "agriinsight-api-v1.openapi.json");
    private static final Path GENERATED = Path.of(
            "target", "generated-contracts", "agriinsight-api-v1.openapi.json");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private IdentityBootstrapPort bootstrapPort;

    @Test
    void writesCanonicalArtifactAndRejectsCheckedInDrift() throws Exception {
        String response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String canonical = response.replace("\r\n", "\n").stripTrailing() + "\n";

        Files.createDirectories(GENERATED.getParent());
        Files.writeString(GENERATED, canonical, StandardCharsets.UTF_8);

        if (!Boolean.getBoolean("agriinsight.openapi.skip-source-check")) {
            assertThat(SOURCE).exists();
            assertThat(Files.readString(SOURCE, StandardCharsets.UTF_8)).isEqualTo(canonical);
        }
    }
}
