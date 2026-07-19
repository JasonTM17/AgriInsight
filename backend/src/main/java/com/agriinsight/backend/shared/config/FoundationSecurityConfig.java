package com.agriinsight.backend.shared.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.agriinsight.backend.shared.web.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class FoundationSecurityConfig {

    @Bean
    SecurityFilterChain foundationSecurityFilterChain(
            HttpSecurity http,
            Environment environment,
            JsonMapper jsonMapper,
            @Value("${agriinsight.api-docs.enabled:false}") boolean apiDocsEnabled) throws Exception {
        boolean publicApiDocs = apiDocsEnabled && environment.acceptsProfiles(Profiles.of("dev", "local"));
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                request,
                                response,
                                jsonMapper,
                                HttpStatus.UNAUTHORIZED,
                                "Authentication required",
                                "Authentication is required to access this resource."))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                request,
                                response,
                                jsonMapper,
                                HttpStatus.FORBIDDEN,
                                "Access denied",
                                "Access to this resource is denied.")))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(
                                    "/actuator/health",
                                    "/actuator/health/liveness",
                                    "/actuator/health/readiness")
                            .permitAll();
                    if (publicApiDocs) {
                        authorize.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .permitAll();
                    }
                    authorize.anyRequest().denyAll();
                });
        return http.build();
    }

    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            JsonMapper jsonMapper,
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
