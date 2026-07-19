package com.agriinsight.backend.architecture;

import com.agriinsight.backend.AgriInsightBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThatCode;

class ModuleBoundaryTests {

    @Test
    void applicationModulesHaveNoCyclesOrIllegalDependencies() {
        assertThatCode(() -> ApplicationModules.of(AgriInsightBackendApplication.class).verify())
                .doesNotThrowAnyException();
    }
}
