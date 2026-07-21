package com.agriinsight.backend.authorization.infrastructure;

import com.agriinsight.backend.shared.persistence.TenantContextBinder;
import com.agriinsight.backend.shared.persistence.TenantContextRequiredException;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedException;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedRecorder;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Objects;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Aspect
@Component
@Profile("!test")
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class TenantTransactionAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantTransactionAspect.class);

    private final TenantContextBinder contextBinder;
    private final TenantContextState contextState;
    private final TransactionTemplate transaction;
    private final TenantAuthorizationDeniedRecorder deniedRecorder;

    public TenantTransactionAspect(
            TenantContextBinder contextBinder,
            TenantContextState contextState,
            PlatformTransactionManager transactionManager) {
        this(contextBinder, contextState, transactionManager, decision -> { });
    }

    public TenantTransactionAspect(
            TenantContextBinder contextBinder,
            TenantContextState contextState,
            PlatformTransactionManager transactionManager,
            TenantAuthorizationDeniedRecorder deniedRecorder) {
        this.contextBinder = Objects.requireNonNull(contextBinder, "contextBinder is required");
        this.contextState = Objects.requireNonNull(contextState, "contextState is required");
        this.transaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager is required"));
        this.transaction.setName("tenant-scoped-service");
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.deniedRecorder = Objects.requireNonNull(deniedRecorder, "deniedRecorder is required");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TenantTransactionAspect(
            TenantContextBinder contextBinder,
            TenantContextState contextState,
            PlatformTransactionManager transactionManager,
            ObjectProvider<TenantAuthorizationDeniedRecorder> deniedRecorder) {
        this(
                contextBinder,
                contextState,
                transactionManager,
                Objects.requireNonNull(deniedRecorder, "deniedRecorder provider is required")
                        .getIfAvailable(() -> decision -> { }));
    }

    @Around("@within(com.agriinsight.backend.authorization.infrastructure.TenantScoped)"
            + " || @annotation(com.agriinsight.backend.authorization.infrastructure.TenantScoped)")
    public Object withinTenantTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        Objects.requireNonNull(joinPoint, "joinPoint is required");
        TenantPrincipal principal = currentPrincipal();
        if (contextState.currentTenantId().isPresent()) {
            requireSameTenant(principal.tenantId());
            return joinPoint.proceed();
        }

        try {
            return transaction.execute(status -> invokeBound(joinPoint, principal.tenantId()));
        } catch (TenantAuthorizationDeniedException exception) {
            recordDenied(exception);
            throw exception;
        } catch (CheckedInvocationException exception) {
            throw exception.getCause();
        }
    }

    private void recordDenied(TenantAuthorizationDeniedException exception) {
        try {
            deniedRecorder.record(exception.decision());
            exception.markAuditRecorded();
        } catch (RuntimeException auditFailure) {
            LOGGER.error(
                    "security.authorization_denied_audit_failed tenantId={} principalId={} errorType={}",
                    exception.decision().tenantId(),
                    exception.decision().principalId(),
                    auditFailure.getClass().getSimpleName());
        }
    }

    private Object invokeBound(ProceedingJoinPoint joinPoint, UUID tenantId) {
        contextBinder.bind(tenantId);
        contextState.bind(tenantId);
        try {
            return joinPoint.proceed();
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new CheckedInvocationException(exception);
        } finally {
            contextState.unbind();
        }
    }

    private TenantPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new TenantContextRequiredException("Tenant principal is required");
        }
        return principal;
    }

    private void requireSameTenant(UUID tenantId) {
        if (!contextState.currentTenantId().filter(tenantId::equals).isPresent()) {
            throw new TenantContextRequiredException("Nested tenant scope cannot switch tenants");
        }
    }

    private static final class CheckedInvocationException extends RuntimeException {

        private CheckedInvocationException(Throwable cause) {
            super(cause);
        }
    }
}
