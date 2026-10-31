package com.democorp.warroom.common.autoconfigure;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.AntPathMatcher;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Every service checks the Keycloak JWT itself (the gateway checks it too).
 * Keycloak realm roles (VIEWER, RESPONDER, APPROVER, ADMIN) become ROLE_* authorities,
 * so controllers can use {@code @PreAuthorize("hasRole('APPROVER')")}.
 */
@AutoConfiguration(before = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class})
@ConditionalOnClass({HttpSecurity.class, JwtAuthenticationConverter.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "warroom.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(WarroomSecurityProperties.class)
public class WarroomSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain warroomSecurityFilterChain(HttpSecurity http, WarroomSecurityProperties props) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SSE/async responses re-dispatch after the original (already authorised) request
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(props.allPublicPaths().toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(ignoringPublicPaths(props.allPublicPaths()))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakConverter())));
        return http.build();
    }

    /**
     * Public paths may carry their own "Bearer" credential (e.g. Alertmanager's webhook API key),
     * so the JWT filter must not try to parse it there.
     */
    static BearerTokenResolver ignoringPublicPaths(List<String> publicPaths) {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        AntPathMatcher matcher = new AntPathMatcher();
        return request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean isPublic = publicPaths.stream().anyMatch(p -> matcher.match(p, path));
            return isPublic ? null : delegate.resolve(request);
        };
    }

    static JwtAuthenticationConverter keycloakConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(realmRoles());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    @SuppressWarnings("unchecked")
    static Converter<Jwt, Collection<GrantedAuthority>> realmRoles() {
        return jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return ((Collection<Object>) roles).stream()
                    .map(String::valueOf)
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        };
    }
}
