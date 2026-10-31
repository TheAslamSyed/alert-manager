package com.democorp.warroom.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param apiKey      shared key webhooks must send (X-API-Key or Authorization: Bearer)
 * @param dedupWindow repeats of the same fingerprint inside this window are dropped
 */
@ConfigurationProperties(prefix = "warroom.ingest")
public record IngestProperties(String apiKey, Duration dedupWindow) {
}
