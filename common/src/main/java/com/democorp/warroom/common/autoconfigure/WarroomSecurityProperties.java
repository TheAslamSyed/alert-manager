package com.democorp.warroom.common.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * @param enabled     turn JWT security off only for local experiments
 * @param publicPaths extra paths that need no JWT (each service adds its own, e.g. /ingest/**)
 */
@ConfigurationProperties(prefix = "warroom.security")
public record WarroomSecurityProperties(Boolean enabled, List<String> publicPaths) {

    private static final List<String> ALWAYS_PUBLIC = List.of(
            "/actuator/health/**", "/actuator/health", "/actuator/info", "/actuator/prometheus",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error");

    public List<String> allPublicPaths() {
        List<String> all = new ArrayList<>(ALWAYS_PUBLIC);
        if (publicPaths != null) {
            all.addAll(publicPaths);
        }
        return all;
    }
}
