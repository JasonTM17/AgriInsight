package com.agriinsight.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agriinsight.api-docs.enabled=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocsExposureTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void enablingDocsDoesNotMakeThemPublicOutsideDevelopmentProfiles() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("X-Correlation-Id", "docs-denied-01"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "docs-denied-01"))
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }
}
