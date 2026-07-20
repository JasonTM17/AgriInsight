package com.agriinsight.backend.shared.api;

import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedException;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedRecorder;
import com.agriinsight.backend.shared.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void malformedJsonReturnsGenericProblemDetailWithCorrelationId() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .header(CorrelationIdFilter.HEADER, "contract-malformed-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.correlationId").value("contract-malformed-01"))
                .andExpect(content().string(not(containsString("JsonEOFException"))));
    }

    @Test
    void invalidFieldsReturnStableErrorsWithoutRejectedValues() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.name").value("value is invalid"));
    }

    @Test
    void integrityFailuresReturnConflictWithoutDatabaseDiagnostics() throws Exception {
        mockMvc.perform(post("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Request conflicts with existing data"))
                .andExpect(content().string(not(containsString("duplicate tenant secret"))));
    }

    @Test
    void invalidMethodParametersReturnGenericBadRequest() throws Exception {
        mockMvc.perform(get("/test/page").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(content().string(not(containsString("must be greater"))));
    }

    @Test
    void missingHeadersAndIllegalArgumentsReturnGenericBadRequests() throws Exception {
        mockMvc.perform(get("/test/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        mockMvc.perform(get("/test/argument"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("sensitive parser detail"))));
    }

    @Test
    void authorizationDenialsReturnStableForbiddenProblemDetails() throws Exception {
        mockMvc.perform(get("/test/tenant-denied")
                        .header(CorrelationIdFilter.HEADER, "denied-handler-01"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Access denied"))
                .andExpect(jsonPath("$.correlationId").value("denied-handler-01"))
                .andExpect(content().string(not(containsString("permission=COST_READ"))));

        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    record TestRequest(@NotBlank String name) {
    }

    @RestController
    static class TestController {

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @PostMapping("/test/conflict")
        void conflict() {
            throw new DataIntegrityViolationException("duplicate tenant secret");
        }

        @GetMapping("/test/page")
        void page(@RequestParam @Min(1) int page) {
        }

        @GetMapping("/test/header")
        void header(@RequestHeader("Required-Header") String value) {
        }

        @GetMapping("/test/argument")
        void argument() {
            throw new IllegalArgumentException("sensitive parser detail");
        }

        @GetMapping("/test/tenant-denied")
        void tenantDenied() {
            throw new TenantAuthorizationDeniedException(new TenantAuthorizationDeniedRecorder.Decision(
                    UUID.fromString("10000000-0000-0000-0000-000000000001"),
                    UUID.fromString("20000000-0000-0000-0000-000000000001"),
                    "permission=COST_READ;scope=TENANT",
                    "MISSING_PERMISSION",
                    Optional.empty(),
                    Optional.empty()));
        }

        @GetMapping("/test/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("private denial detail");
        }
    }
}
