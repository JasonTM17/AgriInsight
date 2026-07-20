package com.agriinsight.backend.identity.infrastructure;

import com.agriinsight.backend.identity.application.ExternalIdentityService;
import com.agriinsight.backend.identity.application.PrincipalMapper;
import com.agriinsight.backend.shared.api.SecurityProblemWriter;
import com.agriinsight.backend.shared.api.SecuredRouteRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(OidcIdentityProperties.class)
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class IdentitySecurityConfig {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(OidcIdentityProperties properties) {
        NimbusJwtDecoder decoder = properties.jwkSetUri() == null
                ? NimbusJwtDecoder.withIssuerLocation(properties.issuerUri())
                        .validateType(false)
                        .jwsAlgorithm(properties.jwsAlgorithm())
                        .build()
                : NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                        .validateType(false)
                        .jwsAlgorithm(properties.jwsAlgorithm())
                        .build();
        decoder.setJwtValidator(new OidcJwtValidator(properties));
        return decoder;
    }

    @Bean
    PrincipalMapper principalMapper(ExternalIdentityService identityService, OidcIdentityProperties properties) {
        return new PrincipalMapper(
                identityService,
                properties.displayNameClaim(),
                properties.emailClaim(),
                properties.assuranceClaim());
    }

    @Bean
    JwtPrincipalAuthenticationConverter jwtPrincipalAuthenticationConverter(PrincipalMapper principalMapper) {
        return new JwtPrincipalAuthenticationConverter(principalMapper);
    }

    @Bean
    CorsConfigurationSource identityCorsConfigurationSource(OidcIdentityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.corsAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.IF_MATCH,
                "Idempotency-Key",
                "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of(HttpHeaders.ETAG, "X-Correlation-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain identitySecurityFilterChain(
            HttpSecurity http,
            Environment environment,
            JwtDecoder jwtDecoder,
            JwtPrincipalAuthenticationConverter authenticationConverter,
            SecuredRouteRegistry routeRegistry,
            CorsConfigurationSource identityCorsConfigurationSource,
            SecurityProblemWriter problemWriter) throws Exception {
        boolean publicApiDocs = environment.acceptsProfiles(Profiles.of("dev", "local"))
                && environment.getProperty("agriinsight.api-docs.enabled", Boolean.class, false);
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(identityCorsConfigurationSource))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                problemWriter.authenticationRequired(request, response))
                        .accessDeniedHandler((request, response, exception) ->
                                problemWriter.accessDenied(request, response, exception)))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint((request, response, exception) ->
                                problemWriter.authenticationRequired(request, response))
                        .accessDeniedHandler((request, response, exception) ->
                                problemWriter.accessDenied(request, response, exception))
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(authenticationConverter)))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(
                                    PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/actuator/health"),
                                    PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/actuator/health/liveness"),
                                    PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/actuator/health/readiness"))
                            .permitAll();
                    if (publicApiDocs) {
                        authorize.requestMatchers(
                                        PathPatternRequestMatcher.pathPattern("/v3/api-docs/**"),
                                        PathPatternRequestMatcher.pathPattern("/swagger-ui/**"),
                                        PathPatternRequestMatcher.pathPattern("/swagger-ui.html"))
                                .permitAll();
                    }
                    routeRegistry.routes().forEach(route -> route.requiredAuthority().ifPresentOrElse(
                            authority -> authorize.requestMatchers(route.requestMatcher())
                                    .hasAuthority(authority),
                            () -> authorize.requestMatchers(route.requestMatcher()).authenticated()));
                    authorize.anyRequest().denyAll();
                });
        return http.build();
    }
}
