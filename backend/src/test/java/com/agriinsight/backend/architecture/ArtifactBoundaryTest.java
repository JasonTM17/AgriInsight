package com.agriinsight.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArtifactBoundaryTest {

    @Test
    void backendSourceCannotResolveTheAnalyticsArtifactTree() throws IOException {
        try (var files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                assertThat(Files.readString(file))
                        .as("backend artifact path reference in %s", file)
                        .doesNotContain("artifacts/", "artifacts\\", "/app/artifacts");
            }
        }
    }

    @Test
    void backendComposeHasNoAnalyticsArtifactMount() throws IOException {
        String compose = Files.readString(Path.of("..", "compose.backend.yaml"));

        assertThat(compose)
                .doesNotContain("./artifacts", "/app/artifacts")
                .contains("./backend/.runtime/postgres:/var/lib/postgresql/data")
                .contains("127.0.0.1:${AGRIINSIGHT_BACKEND_PORT:-8080}:8080");
    }

    @Test
    void backendBuildContextIsAnExplicitAllowlist() throws IOException {
        assertThat(Files.readString(Path.of(".dockerignore")))
                .startsWith("**")
                .contains("!pom.xml", "!src/**")
                .doesNotContain("!.env", "!target", "!artifacts");
    }
}
