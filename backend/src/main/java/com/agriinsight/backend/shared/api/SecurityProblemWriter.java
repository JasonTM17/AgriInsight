package com.agriinsight.backend.shared.api;

import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedRecorder;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedException;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityProblemWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityProblemWriter.class);

    private final JsonMapper jsonMapper;
    private final ObjectProvider<TenantAuthorizationDeniedRecorder> deniedRecorder;

    public SecurityProblemWriter(
            JsonMapper jsonMapper,
            ObjectProvider<TenantAuthorizationDeniedRecorder> deniedRecorder) {
        this.jsonMapper = jsonMapper;
        this.deniedRecorder = Objects.requireNonNull(
                deniedRecorder,
                "deniedRecorder provider is required");
    }

    public void authenticationRequired(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LOGGER.warn(
                "security.authentication_required correlationId={} method={} path={}",
                RequestCorrelation.resolve(request),
                request.getMethod(),
                request.getRequestURI());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "Authentication is required to access this resource.");
    }

    public void accessDenied(HttpServletRequest request, HttpServletResponse response) throws IOException {
        accessDenied(request, response, null);
    }

    public void accessDenied(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        String correlationId = RequestCorrelation.resolve(request);
        if (!(exception instanceof TenantAuthorizationDeniedException denial && denial.auditRecorded())) {
            recordDenied(request, correlationId, exception);
        }
        write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "Access denied",
                "Access to this resource is denied.");
    }

    private void recordDenied(
            HttpServletRequest request,
            String correlationId,
            AccessDeniedException exception) {
        TenantAuthorizationDeniedRecorder.Decision decision = exception instanceof TenantAuthorizationDeniedException denial
                ? denial.decision()
                : null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        TenantAuthorizationDeniedRecorder recorder = deniedRecorder.getIfAvailable();
        if (recorder == null) {
            return;
        }
        if (decision == null) {
            if (authentication == null
                    || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
                return;
            }
            String targetReference = request.getRequestURI();
            if (targetReference.length() > 200) {
                targetReference = targetReference.substring(0, 200);
            }
            decision = new TenantAuthorizationDeniedRecorder.Decision(
                    principal.tenantId(),
                    principal.profileId(),
                    targetReference,
                    "ROUTE_PERMISSION_DENIED",
                    Optional.empty(),
                    Optional.of(correlationId));
        } else if (decision.correlationId().isEmpty()) {
            decision = new TenantAuthorizationDeniedRecorder.Decision(
                    decision.tenantId(),
                    decision.principalId(),
                    decision.targetReference(),
                    decision.reasonCode(),
                    decision.targetId(),
                    Optional.of(correlationId));
        }
        try {
            recorder.record(decision);
            if (exception instanceof TenantAuthorizationDeniedException denial) {
                denial.markAuditRecorded();
            }
        } catch (RuntimeException auditFailure) {
            LOGGER.error(
                    "security.authorization_denied_audit_failed tenantId={} principalId={} errorType={}",
                    decision.tenantId(),
                    decision.principalId(),
                    auditFailure.getClass().getSimpleName());
        }
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail) throws IOException {
        String correlationId = RequestCorrelation.resolve(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("correlationId", correlationId);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(RequestCorrelation.HEADER, correlationId);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
