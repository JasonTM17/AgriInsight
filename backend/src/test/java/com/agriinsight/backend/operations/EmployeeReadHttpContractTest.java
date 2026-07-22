package com.agriinsight.backend.operations;

import static com.agriinsight.backend.operations.EmployeeHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.operations.EmployeeHttpTestSupport.EMPLOYEE_ID;
import static com.agriinsight.backend.operations.EmployeeHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.operations.EmployeeHttpTestSupport.employee;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.operations.application.EmployeePage;
import com.agriinsight.backend.operations.application.EmployeeService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class EmployeeReadHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private EmployeeService employees;

    @Test
    void fullReadAndPickerReadExposeSeparateContracts() throws Exception {
        stubIdentity(jwtDecoder, principalLoader,
                Set.of(Permission.WORKFORCE_MANAGE, Permission.WORKFORCE_PICKER_READ));
        when(employees.list(any())).thenReturn(new EmployeePage(List.of(employee(4)), 25, 0, false));
        when(employees.get(EMPLOYEE_ID)).thenReturn(employee(4));
        when(employees.eligible(any())).thenReturn(new EmployeePage(List.of(employee(4)), 25, 0, false));

        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].jobTitle").value("Technician"));
        mockMvc.perform(get("/api/v1/employees/{id}", EMPLOYEE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""));
        mockMvc.perform(get("/api/v1/employees/eligible")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("WORKER-A"))
                .andExpect(jsonPath("$.items[0].jobTitle").doesNotExist())
                .andExpect(jsonPath("$.items[0].version").doesNotExist());
    }

    @Test
    void permissionsAndBoundsStopBeforeServices() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of());
        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(employees);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.WORKFORCE_MANAGE));
        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(employees);
    }
}
