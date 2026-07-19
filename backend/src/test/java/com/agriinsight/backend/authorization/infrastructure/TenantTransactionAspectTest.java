package com.agriinsight.backend.authorization.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.shared.persistence.TenantContextBinder;
import com.agriinsight.backend.shared.persistence.TenantContextRequiredException;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TenantTransactionAspectTest {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private final TenantContextBinder contextBinder = mock(TenantContextBinder.class);
    private final TenantContextState contextState = new TenantContextState();
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private TenantTransactionAspect aspect;

    @BeforeEach
    void createAspect() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            return transactionStatus;
        });
        doAnswer(invocation -> {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            return null;
        }).when(transactionManager).commit(transactionStatus);
        doAnswer(invocation -> {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            return null;
        }).when(transactionManager).rollback(transactionStatus);
        aspect = new TenantTransactionAspect(contextBinder, contextState, transactionManager);
        authenticate(TENANT_ID);
    }

    @AfterEach
    void clearThreadState() {
        SecurityContextHolder.clearContext();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void bindsTenantBeforeServiceAndOwnsARequiresNewTransaction() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("result");

        assertThat(aspect.withinTenantTransaction(joinPoint)).isEqualTo("result");

        ArgumentCaptor<TransactionDefinition> definition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        var order = inOrder(transactionManager, contextBinder, joinPoint);
        order.verify(transactionManager).getTransaction(definition.capture());
        order.verify(contextBinder).bind(TENANT_ID);
        order.verify(joinPoint).proceed();
        order.verify(transactionManager).commit(transactionStatus);
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void nestedTenantScopedCallsReuseTheOwnedBoundary() throws Throwable {
        ProceedingJoinPoint outer = mock(ProceedingJoinPoint.class);
        ProceedingJoinPoint nested = mock(ProceedingJoinPoint.class);
        when(nested.proceed()).thenReturn("nested-result");
        when(outer.proceed()).thenAnswer(invocation -> aspect.withinTenantTransaction(nested));

        assertThat(aspect.withinTenantTransaction(outer)).isEqualTo("nested-result");

        verify(transactionManager).getTransaction(any());
        verify(contextBinder).bind(TENANT_ID);
        verify(outer).proceed();
        verify(nested).proceed();
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void missingTenantPrincipalFailsBeforeOpeningAConnection() {
        SecurityContextHolder.clearContext();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        assertThatThrownBy(() -> aspect.withinTenantTransaction(joinPoint))
                .isInstanceOf(TenantContextRequiredException.class)
                .hasMessage("Tenant principal is required");
        verify(transactionManager, never()).getTransaction(any());
        verifyNoInteractions(contextBinder);
    }

    @Test
    void nestedScopeCannotSwitchToAnotherTenant() throws Throwable {
        UUID otherTenant = UUID.fromString("10000000-0000-0000-0000-000000000002");
        ProceedingJoinPoint outer = mock(ProceedingJoinPoint.class);
        ProceedingJoinPoint nested = mock(ProceedingJoinPoint.class);
        when(outer.proceed()).thenAnswer(invocation -> {
            authenticate(otherTenant);
            return aspect.withinTenantTransaction(nested);
        });

        assertThatThrownBy(() -> aspect.withinTenantTransaction(outer))
                .isInstanceOf(TenantContextRequiredException.class)
                .hasMessage("Nested tenant scope cannot switch tenants");
        verify(transactionManager).rollback(transactionStatus);
        verify(nested, never()).proceed();
    }

    @Test
    void tenantScopedAnnotationRoutesCallsThroughTheAspect() {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new AnnotatedService());
        proxyFactory.addAspect(aspect);
        AnnotatedService service = proxyFactory.getProxy();

        assertThat(service.execute()).isEqualTo("annotated-result");

        verify(contextBinder).bind(TENANT_ID);
        verify(transactionManager).commit(transactionStatus);
    }

    private void authenticate(UUID tenantId) {
        TenantPrincipal principal = new TestPrincipal(PROFILE_ID, tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private record TestPrincipal(UUID profileId, UUID tenantId) implements TenantPrincipal {

        @Override
        public String getName() {
            return profileId.toString();
        }
    }

    @TenantScoped
    static class AnnotatedService {

        public String execute() {
            return "annotated-result";
        }
    }
}
