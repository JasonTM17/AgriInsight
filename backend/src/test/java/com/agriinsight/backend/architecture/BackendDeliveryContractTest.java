package com.agriinsight.backend.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BackendDeliveryContractTest {

    @Test
    void dockerBuilderProvidesZipExtractionBeforeRunningTheScriptOnlyWrapper() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        int unzipInstall = dockerfile.indexOf("--no-install-recommends unzip");
        int wrapperExecution = dockerfile.indexOf("./mvnw");

        assertTrue(unzipInstall >= 0, "The script-only Maven wrapper requires unzip in the clean builder image");
        assertTrue(wrapperExecution > unzipInstall, "unzip must be available before the Maven wrapper downloads its ZIP");
    }
}
