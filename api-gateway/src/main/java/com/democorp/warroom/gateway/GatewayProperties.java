package com.democorp.warroom.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "warroom.gateway")
public record GatewayProperties(String incidentUrl, String remediationUrl, String knowledgeUrl, String auditUrl,
                                String democorpUrl, List<String> allowedOrigins) {
}
