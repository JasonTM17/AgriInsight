package com.agriinsight.backend;

import com.agriinsight.backend.shared.api.ApiVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.endpoint.health.group.readiness.include=readinessState")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendContextTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void applicationContextLoadsWithSafeTestProfile() {
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        assertThat(environment.getProperty(
                "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access", Boolean.class)).isFalse();
        assertThat(environment.getProperty(
                "spring.jackson.deserialization.fail-on-unknown-properties", Boolean.class)).isTrue();
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.datasource.hikari.data-source-properties.connectTimeout"))
                .isEqualTo("3");
        assertThat(environment.getProperty("spring.datasource.hikari.data-source-properties.socketTimeout"))
                .isEqualTo("5");
    }

    @Test
    void livenessIsPublicAndDoesNotExposeDiagnostics() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")
                        .header("X-Correlation-Id", "test-correlation-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-correlation-01"))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("schema"))))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void readinessIsPublicWithNoExternalDependencyInTestProfile() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));
    }

    @Test
    void versionedBusinessRoutesAreNeverPublicBeforeIdentityIsConfigured() throws Exception {
        mockMvc.perform(get(ApiVersion.PREFIX + "/farms")
                        .header("X-Correlation-Id", "denied-route-01"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "denied-route-01"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.correlationId").value("denied-route-01"));
    }

    @Test
    void healthComponentsAndOtherActuatorEndpointsAreNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().is(anyOf(is(401), is(403))));
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().is(anyOf(is(401), is(403), is(404))));
    }
}
