package com.democorp.warroom.remediation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "warroom.remediation")
public record RemediationProperties(String democorpUrl, String democorpAdminKey, String prometheusUrl,
                                    String signingKey, Duration verifyTimeout) {
}
