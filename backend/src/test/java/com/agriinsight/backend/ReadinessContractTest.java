package com.agriinsight.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoint.health.group.readiness.include=readinessState,schemaHistory"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReadinessContractTest.BehindSchemaConfiguration.class)
class ReadinessContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void behindSchemaMakesReadinessUnavailableWithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("installedVersion"))))
                .andExpect(content().string(not(containsString("expectedVersion"))));
    }

    @Test
    void behindSchemaNeverChangesProcessLiveness() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BehindSchemaConfiguration {

        @Bean("schemaHistory")
        HealthIndicator behindSchemaHistory() {
            return () -> Health.outOfService()
                    .withDetail("installedVersion", "0")
                    .withDetail("expectedVersion", "1")
                    .build();
        }
    }
}
