package com.democorp.warroom.ingestion.api;

import com.democorp.warroom.ingestion.IngestProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Webhooks (Alertmanager, CI, ticketing) authenticate with a shared key, not a user login. */
@Configuration
public class ApiKeyFilter {

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> ingestApiKeyFilter(IngestProperties props) {
        byte[] expected = props.apiKey().getBytes(StandardCharsets.UTF_8);
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String key = request.getHeader("X-API-Key");
                String auth = request.getHeader("Authorization");
                if (key == null && auth != null && auth.startsWith("Bearer ")) {
                    key = auth.substring("Bearer ".length());
                }
                if (key == null || !MessageDigest.isEqual(expected, key.getBytes(StandardCharsets.UTF_8))) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or wrong API key");
                    return;
                }
                chain.doFilter(request, response);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/ingest/*");
        registration.setName("ingestApiKeyFilter");
        return registration;
    }
}
