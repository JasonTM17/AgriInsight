package com.agriinsight.backend.shared.web;

import com.agriinsight.backend.shared.api.RequestCorrelation;
import java.io.IOException;

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

    public static final String HEADER = RequestCorrelation.HEADER;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = RequestCorrelation.resolve(request);
        response.setHeader(HEADER, correlationId);
        filterChain.doFilter(request, response);
    }

    public static String resolve(HttpServletRequest request) {
        return RequestCorrelation.resolve(request);
    }
}
