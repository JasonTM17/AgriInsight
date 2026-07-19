package com.agriinsight.backend.shared.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final int MAX_LENGTH = 128;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolve(request);
        response.setHeader(HEADER, correlationId);
        filterChain.doFilter(request, response);
    }

    public static String resolve(HttpServletRequest request) {
        Object attribute = request.getAttribute(HEADER);
        if (attribute instanceof String value && isSafe(value)) {
            return value;
        }
        String supplied = request.getHeader(HEADER);
        String correlationId = isSafe(supplied) ? supplied : UUID.randomUUID().toString();
        request.setAttribute(HEADER, correlationId);
        return correlationId;
    }

    private static boolean isSafe(String value) {
        return value != null && value.length() <= MAX_LENGTH && SAFE_ID.matcher(value).matches();
    }
}
