package com.agriinsight.backend.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalCommandHasherTest {

    private static final String USER_ID = "20000000-0000-0000-0000-000000000001";

    private final CanonicalCommandHasher hasher = new CanonicalCommandHasher();

    @Test
    void versionOneFingerprintHasAStableRegressionVector() {
        CanonicalCommandMaterial command = command(
                Map.of("id", USER_ID),
                Map.of("expand", List.of("roles", "identities")),
                Map.of("If-Match", "\"7\""));

        CanonicalCommandHasher.Fingerprint fingerprint =
                hasher.fingerprint((short) 1, command);

        assertThat(fingerprint.httpMethod()).isEqualTo("PATCH");
        assertThat(fingerprint.routeTemplate()).isEqualTo("/api/v1/users/{id}");
        assertThat(fingerprint.commandHash())
                .isEqualTo("ca33d672b8e9e7447570856cd119c794f25eb7e10a75cd60e3f8c693e2f7a455");
    }

    @Test
    void mapInsertionOrderDoesNotChangeTheFingerprint() {
        Map<String, String> orderedPath = new LinkedHashMap<>();
        orderedPath.put("tenant", "TENANT-A");
        orderedPath.put("id", USER_ID);
        Map<String, String> reversedPath = new LinkedHashMap<>();
        reversedPath.put("id", USER_ID);
        reversedPath.put("tenant", "TENANT-A");

        CanonicalCommandHasher.Fingerprint first = hasher.fingerprint(
                (short) 1,
                command(orderedPath, Map.of("view", List.of("full")), Map.of("If-Match", "\"7\"")));
        CanonicalCommandHasher.Fingerprint second = hasher.fingerprint(
                (short) 1,
                command(reversedPath, Map.of("view", List.of("full")), Map.of("if-match", "\"7\"")));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void pathPreconditionAndSchemaVersionAreBoundIntoTheHash() {
        CanonicalCommandMaterial original = command(
                Map.of("id", USER_ID),
                Map.of(),
                Map.of("If-Match", "\"7\""));

        String originalHash = hasher.fingerprint((short) 1, original).commandHash();
        String changedPathHash = hasher.fingerprint(
                (short) 1,
                command(
                        Map.of("id", "20000000-0000-0000-0000-000000000002"),
                        Map.of(),
                        Map.of("If-Match", "\"7\"")))
                .commandHash();
        String changedPreconditionHash = hasher.fingerprint(
                (short) 1,
                command(Map.of("id", USER_ID), Map.of(), Map.of("If-Match", "\"8\"")))
                .commandHash();
        String changedVersionHash = hasher.fingerprint((short) 2, original).commandHash();

        assertThat(changedPathHash).isNotEqualTo(originalHash);
        assertThat(changedPreconditionHash).isNotEqualTo(originalHash);
        assertThat(changedVersionHash).isNotEqualTo(originalHash);
    }

    @Test
    void materialRejectsUnregisteredRoutesAndCredentialHeaders() {
        assertThatThrownBy(() -> command(Map.of(), Map.of(), Map.of("Authorization", "secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("semantic header");
        assertThatThrownBy(() -> new CanonicalCommandMaterial(
                        "PATCH",
                        "/internal/users/{id}",
                        Map.of(),
                        Map.of(),
                        "{}",
                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("routeTemplate");
    }

    private CanonicalCommandMaterial command(
            Map<String, String> path,
            Map<String, List<String>> query,
            Map<String, String> headers) {
        return new CanonicalCommandMaterial(
                "patch",
                "/api/v1/users/{id}",
                path,
                query,
                "{\"active\":false,\"displayName\":\"Admin A\"}",
                headers);
    }
}
