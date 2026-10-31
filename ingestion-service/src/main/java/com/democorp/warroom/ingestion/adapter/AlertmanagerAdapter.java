package com.democorp.warroom.ingestion.adapter;

import com.democorp.warroom.common.contracts.Severity;
import com.democorp.warroom.common.contracts.SignalKind;
import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.ingestion.api.IngestRequests.AlertmanagerWebhook;
import com.democorp.warroom.ingestion.api.IngestRequests.AlertmanagerWebhook.Alert;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Prometheus Alertmanager webhook → one signal per alert (firing or resolved). */
@Component
public class AlertmanagerAdapter implements SignalAdapter<AlertmanagerWebhook> {

    @Override
    public List<SignalPayload> toSignals(AlertmanagerWebhook webhook) {
        return webhook.alerts().stream().map(this::toSignal).toList();
    }

    private SignalPayload toSignal(Alert alert) {
        Map<String, String> labels = alert.labels() == null ? Map.of() : alert.labels();
        Map<String, String> annotations = alert.annotations() == null ? Map.of() : alert.annotations();
        String alertName = labels.getOrDefault("alertname", "UnnamedAlert");
        String status = alert.status() == null ? "firing" : alert.status();

        Map<String, String> attributes = new HashMap<>(labels);
        attributes.put("alertStatus", status);
        if (alert.generatorURL() != null) {
            attributes.put("generatorURL", alert.generatorURL());
        }
        String title = annotations.getOrDefault("summary", alertName);
        Instant occurredAt = "resolved".equals(status) && alert.endsAt() != null ? alert.endsAt()
                : alert.startsAt() != null ? alert.startsAt() : Instant.now();

        return new SignalPayload(
                UUID.randomUUID().toString(),
                SignalKind.ALERT,
                "alertmanager",
                labels.get("service"),
                Severity.fromLabel(labels.get("severity")),
                "resolved".equals(status) ? "Resolved: " + title : title,
                annotations.getOrDefault("description", title),
                SignalAdapter.fingerprint("alert", alert.fingerprint(), alertName, labels.get("service"),
                        status, alert.startsAt()),
                occurredAt,
                attributes,
                null,
                null);
    }
}
