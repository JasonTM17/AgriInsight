package com.agriinsight.backend.operations;

import static com.agriinsight.backend.operations.EmployeeHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.operations.EmployeeHttpTestSupport.EMPLOYEE_ID;
import static com.agriinsight.backend.operations.EmployeeHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.operations.EmployeeHttpTestSupport.completed;
import static com.agriinsight.backend.operations.EmployeeHttpTestSupport.employee;
import static com.agriinsight.backend.operations.EmployeeHttpTestSupport.stubIdentity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.operations.application.EmployeeCommandService;
import com.agriinsight.backend.operations.application.EmployeeCommands;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class EmployeeCreateUpdateHttpContractTest {

    private static final String CREATE_BODY = """
            {"code":" worker-a ","displayName":" Worker A ",
             "jobTitle":" Technician ","reasonCode":"employee_create"}
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private EmployeeCommandService employeeCommands;

    @Test
    void createReturnsCanonicalRepresentationAndLocation() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.WORKFORCE_MANAGE));
        when(employeeCommands.create(any(), any())).thenReturn(completed(201, employee(0)));

        mockMvc.perform(post("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "create-employee-1")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "/api/v1/employees/" + EMPLOYEE_ID))
                .andExpect(jsonPath("$.code").value("WORKER-A"));

        ArgumentCaptor<CommandExecutionRequest> requests =
                ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<EmployeeCommands.Create> commands =
                ArgumentCaptor.forClass(EmployeeCommands.Create.class);
        verify(employeeCommands).create(requests.capture(), commands.capture());
        assertThat(requests.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(commands.getValue().code()).isEqualTo("WORKER-A");
        assertThat(commands.getValue().jobTitle()).contains("Technician");
    }

    @Test
    void unknownFieldsAndWeakIfMatchFailBeforeCommandService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.WORKFORCE_MANAGE));
        mockMvc.perform(post("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-employee-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WORKER-A\",\"displayName\":\"Worker\",\"tenantId\":\"hidden\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/employees/{id}", EMPLOYEE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "weak-employee-patch")
                        .header(HttpHeaders.IF_MATCH, "W/\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(employeeCommands);
    }
}
