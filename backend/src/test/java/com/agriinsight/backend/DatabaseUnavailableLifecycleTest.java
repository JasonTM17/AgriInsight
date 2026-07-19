package com.agriinsight.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/agriinsight",
        "spring.datasource.username=unavailable-test-user",
        "spring.datasource.password=unavailable-test-password",
        "spring.datasource.hikari.connection-timeout=500",
        "spring.datasource.hikari.validation-timeout=250",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.data-source-properties.connectTimeout=1",
        "spring.datasource.hikari.data-source-properties.loginTimeout=1",
        "spring.datasource.hikari.data-source-properties.socketTimeout=1",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "spring.jpa.properties.jakarta.persistence.database-product-name=PostgreSQL",
        "spring.jpa.properties.jakarta.persistence.database-major-version=18",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class DatabaseUnavailableLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void databaseFailureAffectsReadinessWithoutKillingLiveness() throws Exception {
        assertThat(applicationContext.containsBean("schemaHistory")).isTrue();

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("jdbc:postgresql"))))
                .andExpect(content().string(not(containsString("unavailable-test-password"))));
    }
}
