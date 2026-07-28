package com.agriinsight.backend.realtime.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.realtime.api.RealtimeSummaryResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

final class AuthenticatedRealtimeSummaryApi {

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;

    AuthenticatedRealtimeSummaryApi(MockMvc mockMvc, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
    }

    RealtimeSummaryResponse summary(String accessToken) throws Exception {
        String response = mockMvc.perform(get("/api/v1/realtime/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(response, RealtimeSummaryResponse.class);
    }
}
