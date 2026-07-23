package com.agriinsight.backend.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPage;
import com.agriinsight.backend.authorization.application.TenantAuditReadService;
import com.agriinsight.backend.authorization.application.TenantAuditRecord;
import com.agriinsight.backend.authorization.application.TenantRoleAssignment;
import com.agriinsight.backend.authorization.application.TenantRoleAssignmentPage;
import com.agriinsight.backend.authorization.application.TenantRoleAssignmentService;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.farm.application.FarmAssignmentPage;
import com.agriinsight.backend.farm.application.FarmAssignmentRecord;
import com.agriinsight.backend.farm.application.FarmAssignmentService;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.ExternalIdentityPage;
import com.agriinsight.backend.identity.application.ExternalIdentityReference;
import com.agriinsight.backend.identity.application.TenantExternalIdentityReadService;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.inventory.application.SupplierService;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentPage;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentRecord;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class AdminReadCollectionsHttpContractTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID RESOURCE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final String TOKEN = "admin-reads-token";
    private static final String AUTHORIZATION = "Bearer " + TOKEN;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private TenantRoleAssignmentService roles;
    @Autowired private TenantExternalIdentityReadService identities;
    @Autowired private FarmAssignmentService farms;
    @Autowired private WarehouseAssignmentService warehouses;
    @Autowired private TenantAuditReadService auditEvents;
    @Autowired private SupplierService suppliers;

    @Test
    void adminCollectionsAreBoundedAndRedacted() throws Exception {
        stubIdentity(Set.of(
                Permission.IDENTITY_ROLE_MANAGE,
                Permission.IDENTITY_USER_MANAGE,
                Permission.FARM_ASSIGNMENT_MANAGE,
                Permission.INVENTORY_ASSIGNMENT_MANAGE));
        when(roles.list(any(), any())).thenReturn(new TenantRoleAssignmentPage(
                List.of(new TenantRoleAssignment(
                        ASSIGNMENT_ID, TENANT_ID, PROFILE_ID, Role.DATA_ANALYST, true, 0)),
                20, 0, false));
        when(identities.list(any(), any())).thenReturn(new ExternalIdentityPage(
                List.of(new ExternalIdentityReference(ASSIGNMENT_ID, "https://issuer.test", true, 2)),
                20, 0, false));
        when(farms.list(any())).thenReturn(new FarmAssignmentPage(
                List.of(new FarmAssignmentRecord(
                        ASSIGNMENT_ID, TENANT_ID, PROFILE_ID, RESOURCE_ID, Optional.empty(), 0)),
                20, 0, false));
        when(warehouses.list(any())).thenReturn(new WarehouseAssignmentPage(
                List.of(new WarehouseAssignmentRecord(
                        ASSIGNMENT_ID, TENANT_ID, PROFILE_ID, RESOURCE_ID, Optional.empty(), 0)),
                20, 0, false));
        when(auditEvents.list(any())).thenReturn(new TenantAuditPage(
                List.of(new TenantAuditRecord(
                        ASSIGNMENT_ID,
                        TenantAuditRecord.ActorType.TENANT_USER,
                        Optional.of(ACTOR_ID),
                        "ROLE_GRANTED",
                        "USER_ROLE",
                        Optional.of(PROFILE_ID),
                        Optional.of("ACCESS_APPROVED"),
                        Optional.of("request-123"),
                        TenantAuditEvent.Outcome.SUCCEEDED,
                        Instant.parse("2026-07-23T00:00:00Z"))),
                20, 0, false));

        assertPage("/api/v1/users/" + PROFILE_ID + "/roles", "roleCode", "DATA_ANALYST");
        assertPage(
                "/api/v1/users/" + PROFILE_ID + "/external-identities",
                "issuer",
                "https://issuer.test");
        assertPage("/api/v1/farm-assignments", "farmId", RESOURCE_ID.toString());
        assertPage("/api/v1/warehouse-assignments", "warehouseId", RESOURCE_ID.toString());

        mockMvc.perform(get("/api/v1/audit-events")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("ROLE_GRANTED"))
                .andExpect(jsonPath("$.items[0].actorReference").doesNotExist())
                .andExpect(jsonPath("$.items[0].targetReference").doesNotExist())
                .andExpect(jsonPath("$.items[0].scope").doesNotExist())
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist());

        mockMvc.perform(get("/api/v1/users/{id}/external-identities", PROFILE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "20"))
                .andExpect(jsonPath("$.items[0].subject").doesNotExist())
                .andExpect(jsonPath("$.items[0].claims").doesNotExist());
    }

    @Test
    void supplierReadRemainsDeniedForAdministrationPermissions() throws Exception {
        stubIdentity(Set.of(
                Permission.IDENTITY_USER_MANAGE,
                Permission.IDENTITY_ROLE_MANAGE,
                Permission.FARM_ASSIGNMENT_MANAGE,
                Permission.INVENTORY_ASSIGNMENT_MANAGE));

        mockMvc.perform(get("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(suppliers);
    }

    private void assertPage(String path, String field, String value) throws Exception {
        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]." + field).value(value))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    private void stubIdentity(Set<Permission> permissions) {
        when(jwtDecoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("provider-admin")
                .audience(List.of("agriinsight-api"))
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(90))
                .claim("token_use", "access")
                .build());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                ACTOR_ID,
                TENANT_ID,
                "TENANT-A",
                Optional.of("Admin"),
                Optional.empty(),
                Optional.of("mfa"),
                Set.of(Role.TENANT_ADMIN),
                permissions));
    }
}
