package com.agriinsight.backend.shared.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationSafetyTest {

    private static final List<String> SECRET_KEYS = List.of(
            "password", "token", "client-secret", "private-key");
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V(\\d+)__.+[.]sql$");

    @Test
    void sourceConfigurationContainsNoInlineSecretsOrPrivateKeys() throws IOException {
        Path resources = Path.of("src", "main", "resources");

        try (Stream<Path> files = Files.walk(resources)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file);
                assertThat(content)
                        .as("private key material in %s", file)
                        .doesNotContain("-----BEGIN PRIVATE KEY-----")
                        .doesNotContain("-----BEGIN RSA PRIVATE KEY-----")
                        .doesNotContain("-----BEGIN EC PRIVATE KEY-----");
                assertSecretValuesAreEnvironmentBacked(file, content);
            }
        }
    }

    @Test
    void defaultReadinessTracksTheLatestVersionedMigration() throws IOException {
        Path migrations = Path.of("src", "main", "resources", "db", "migration");
        int latest;
        try (Stream<Path> files = Files.list(migrations)) {
            latest = files.map(path -> VERSIONED_MIGRATION.matcher(path.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                    .max()
                    .orElseThrow();
        }

        assertThat(Files.readString(Path.of("src", "main", "resources", "application.yml")))
                .contains("expected-version: ${AGRIINSIGHT_SCHEMA_EXPECTED_VERSION:" + latest + "}");
    }

    private void assertSecretValuesAreEnvironmentBacked(Path file, String content) {
        String[] lines = content.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].strip();
            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String key = line.substring(0, separator).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).strip();
            if (SECRET_KEYS.contains(key)) {
                assertThat(value)
                        .as("inline secret at %s:%d", file, index + 1)
                        .satisfiesAnyOf(
                                candidate -> assertThat(candidate).isEmpty(),
                                candidate -> assertThat(candidate).startsWith("${"));
            }
        }
    }
}
