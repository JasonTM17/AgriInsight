package com.agriinsight.backend.shared.api;

import com.agriinsight.backend.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityProblemWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityProblemWriter.class);

    private final JsonMapper jsonMapper;

    public SecurityProblemWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void authenticationRequired(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LOGGER.warn(
                "security.authentication_required correlationId={} method={} path={}",
                CorrelationIdFilter.resolve(request),
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
        write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "Access denied",
                "Access to this resource is denied.");
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail) throws IOException {
        String correlationId = CorrelationIdFilter.resolve(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("correlationId", correlationId);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(CorrelationIdFilter.HEADER, correlationId);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
