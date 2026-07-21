package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.farm.SeasonHttpTestSupport.SEASON_ID;
import static com.agriinsight.backend.farm.SeasonHttpTestSupport.completed;
import static com.agriinsight.backend.farm.SeasonHttpTestSupport.season;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.farm.application.SeasonCommandService;
import com.agriinsight.backend.farm.application.SeasonCommands;
import com.agriinsight.backend.farm.domain.Season;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import java.time.LocalDate;
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
class SeasonTransitionHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private SeasonCommandService seasonCommands;

    @Test
    void transitionRequiresPermissionIdempotencyStrongVersionAndReason() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of());
        mockMvc.perform(post("/api/v1/seasons/{id}/transition", SEASON_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "transition-denied")
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"ACTIVE\",\"effectiveDate\":\"2027-01-02\",\"reasonCode\":\"START\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(seasonCommands);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.SEASON_MANAGE));
        mockMvc.perform(post("/api/v1/seasons/{id}/transition", SEASON_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "transition-invalid")
                        .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"ACTIVE\",\"effectiveDate\":\"2027-01-02\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(seasonCommands);
    }

    @Test
    void validTransitionReturnsVersionedRepresentation() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.SEASON_MANAGE));
        when(seasonCommands.transition(any(), any(), any()))
                .thenReturn(completed(200, season(1, Season.Status.ACTIVE)));

        mockMvc.perform(post("/api/v1/seasons/{id}/transition", SEASON_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "transition-season-1")
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"ACTIVE\",\"effectiveDate\":\"2027-01-02\",\"reasonCode\":\"season_started\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        ArgumentCaptor<SeasonCommands.Transition> command =
                ArgumentCaptor.forClass(SeasonCommands.Transition.class);
        verify(seasonCommands).transition(any(), org.mockito.ArgumentMatchers.eq(SEASON_ID), command.capture());
        assertThat(command.getValue().targetStatus()).isEqualTo(Season.Status.ACTIVE);
        assertThat(command.getValue().effectiveDate()).isEqualTo(LocalDate.parse("2027-01-02"));
        assertThat(command.getValue().audit().reasonCode()).contains("SEASON_STARTED");
    }
}
