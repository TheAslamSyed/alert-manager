package com.democorp.warroom.incident.correlation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "warroom.correlation")
public record CorrelationProperties(Duration window, Duration contextLookback, double attachThreshold) {
}
