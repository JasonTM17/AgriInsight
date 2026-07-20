package com.agriinsight.backend.shared.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.shared.domain.CanonicalCommandHasher;
import com.agriinsight.backend.shared.persistence.TenantContextRequiredException;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CommandExecutionActorBindingTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID AUTHENTICATED_PROFILE =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SPOOFED_PROFILE =
            UUID.fromString("20000000-0000-0000-0000-000000000002");

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsCommandBoundToAnotherPrincipalBeforeTouchingTheStore() {
        ApiCommandRecordStore store = mock(ApiCommandRecordStore.class);
        TenantContextState contextState = mock(TenantContextState.class);
        TenantPrincipal principal = mock(TenantPrincipal.class);
        when(principal.tenantId()).thenReturn(TENANT_ID);
        when(principal.profileId()).thenReturn(AUTHENTICATED_PROFILE);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        CommandExecutionService service = new CommandExecutionService(
                store,
                contextState,
                mock(IdempotencyConflictPublisher.class));
        CommandExecutionRequest request = new CommandExecutionRequest(
                TENANT_ID,
                SPOOFED_PROFILE,
                IdempotencyKey.parse("spoof-attempt-0001"),
                new CanonicalCommandHasher.Fingerprint(
                        (short) 1,
                        "PATCH",
                        "/api/v1/users/{id}",
                        "a".repeat(64)),
                Optional.empty());

        assertThatThrownBy(() -> service.execute(
                        request,
                        () -> CommandCompletion.withoutRepresentation(
                                204,
                                "USER_PROFILE",
                                AUTHENTICATED_PROFILE,
                                0),
                        target -> Optional.empty()))
                .isInstanceOf(TenantContextRequiredException.class)
                .hasMessageContaining("actor");
        verify(store, never()).claim(org.mockito.ArgumentMatchers.any());
        verify(contextState, never()).requireBound(org.mockito.ArgumentMatchers.any());
    }
}
