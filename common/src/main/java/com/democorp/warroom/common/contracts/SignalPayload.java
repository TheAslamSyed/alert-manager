package com.democorp.warroom.common.contracts;

import java.time.Instant;
import java.util.Map;

/**
 * A normalized signal, whatever tool it came from.
 *
 * @param signalId    unique id
 * @param kind        alert, log alert, deploy, config change or ticket
 * @param source      the tool that sent it (alertmanager, ci, ticketing, ...)
 * @param service     affected service (catalog name), may be null for tickets
 * @param severity    severity hint
 * @param title       one-line summary
 * @param description longer text; treated as untrusted input by the agents
 * @param fingerprint used to drop duplicates
 * @param occurredAt  when it happened at the source
 * @param attributes  extra key/values (version, alertname, ...)
 * @param team        owning team (added from the service catalog)
 * @param tier        service tier, 1 = most critical (added from the service catalog)
 */
public record SignalPayload(
        String signalId,
        SignalKind kind,
        String source,
        String service,
        Severity severity,
        String title,
        String description,
        String fingerprint,
        Instant occurredAt,
        Map<String, String> attributes,
        String team,
        Integer tier) {

    public SignalPayload withEnrichment(String team, Integer tier) {
        return new SignalPayload(signalId, kind, source, service, severity, title, description,
                fingerprint, occurredAt, attributes, team, tier);
    }
}
