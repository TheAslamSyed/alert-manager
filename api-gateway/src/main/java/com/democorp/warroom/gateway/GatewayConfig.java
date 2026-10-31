package com.democorp.warroom.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * API Gateway pattern: the dashboard only ever talks to this one origin.
 * The JWT is checked here and again by every service (the Authorization header is passed through).
 * Internal paths (/internal/**, DemoCorp's /admin/**) are deliberately not routed.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewayConfig {

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder, GatewayProperties p) {
        return builder.routes()
                .route("incidents", r -> r.path("/api/incidents/**", "/api/comms/**").uri(p.incidentUrl()))
                .route("remediations", r -> r.path("/api/remediations/**").uri(p.remediationUrl()))
                .route("knowledge", r -> r.path("/api/knowledge/**").uri(p.knowledgeUrl()))
                .route("audit", r -> r.path("/api/audit/**").uri(p.auditUrl()))
                // Demo control + evaluation: /api/demo/scenarios/... -> democorp-bank /scenarios/...
                .route("democorp", r -> r.path("/api/demo/scenarios/**", "/api/demo/evaluation/**")
                        .filters(f -> f.rewritePath("/api/demo/(?<rest>.*)", "/${rest}"))
                        .uri(p.democorpUrl()))
                .build();
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> { })
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/health", "/actuator/prometheus").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRoles())))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties p) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(p.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Cache-Control"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @SuppressWarnings("unchecked")
    private static Converter<Jwt, Mono<AbstractAuthenticationToken>> keycloakRoles() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            if (realm == null || !(realm.get("roles") instanceof Collection<?> roles)) {
                return List.<GrantedAuthority>of();
            }
            return ((Collection<Object>) roles).stream()
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList();
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
