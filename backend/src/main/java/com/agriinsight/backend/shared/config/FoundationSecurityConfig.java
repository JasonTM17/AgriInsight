package com.agriinsight.backend.shared.config;

import com.agriinsight.backend.shared.api.SecurityProblemWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FoundationSecurityConfig {

    @Bean
    SecurityFilterChain foundationSecurityFilterChain(
            HttpSecurity http,
            Environment environment,
            SecurityProblemWriter problemWriter,
            @Value("${agriinsight.api-docs.enabled:false}") boolean apiDocsEnabled) throws Exception {
        boolean publicApiDocs = apiDocsEnabled && environment.acceptsProfiles(Profiles.of("dev", "local"));
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                problemWriter.authenticationRequired(request, response))
                        .accessDeniedHandler((request, response, exception) ->
                                problemWriter.accessDenied(request, response)))
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
}
