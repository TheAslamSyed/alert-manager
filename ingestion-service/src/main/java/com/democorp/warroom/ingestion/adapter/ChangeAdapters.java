package com.democorp.warroom.ingestion.adapter;

import com.democorp.warroom.common.contracts.Severity;
import com.democorp.warroom.common.contracts.SignalKind;
import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.ingestion.api.IngestRequests.ConfigChangeRequest;
import com.democorp.warroom.ingestion.api.IngestRequests.DeployRequest;
import com.democorp.warroom.ingestion.api.IngestRequests.GenericSignalRequest;
import com.democorp.warroom.ingestion.api.IngestRequests.TicketRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adapters for CI deploys, config changes, customer tickets and generic tools. */
@Configuration
public class ChangeAdapters {

    @Bean
    SignalAdapter<DeployRequest> deployAdapter() {
        return r -> {
            Instant at = r.occurredAt() == null ? Instant.now() : r.occurredAt();
            Map<String, String> attrs = new HashMap<>();
            attrs.put("fromVersion", String.valueOf(r.fromVersion()));
            attrs.put("toVersion", r.toVersion());
            attrs.put("deployedBy", String.valueOf(r.deployedBy()));
            String title = r.service() + " deployed " + r.fromVersion() + " → " + r.toVersion();
            return List.of(new SignalPayload(UUID.randomUUID().toString(), SignalKind.DEPLOY, "ci", r.service(),
                    Severity.SEV4, title, r.changeSummary() == null ? title : r.changeSummary(),
                    SignalAdapter.fingerprint("deploy", r.service(), r.fromVersion(), r.toVersion(), at),
                    at, attrs, null, null));
        };
    }

    @Bean
    SignalAdapter<ConfigChangeRequest> configChangeAdapter() {
        return r -> {
            Instant at = r.occurredAt() == null ? Instant.now() : r.occurredAt();
            Map<String, String> attrs = new HashMap<>();
            attrs.put("key", r.key());
            attrs.put("oldValue", String.valueOf(r.oldValue()));
            attrs.put("newValue", String.valueOf(r.newValue()));
            attrs.put("changedBy", String.valueOf(r.changedBy()));
            String title = r.service() + " config " + r.key() + ": " + r.oldValue() + " → " + r.newValue();
            return List.of(new SignalPayload(UUID.randomUUID().toString(), SignalKind.CONFIG_CHANGE, "config-store",
                    r.service(), Severity.SEV4, title, title,
                    SignalAdapter.fingerprint("config", r.service(), r.key(), r.newValue(), at),
                    at, attrs, null, null));
        };
    }

    @Bean
    SignalAdapter<TicketRequest> ticketAdapter() {
        return r -> {
            Instant at = r.occurredAt() == null ? Instant.now() : r.occurredAt();
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ticketId", String.valueOf(r.ticketId()));
            attrs.put("customerRef", String.valueOf(r.customerRef()));
            return List.of(new SignalPayload(UUID.randomUUID().toString(), SignalKind.TICKET, "ticketing",
                    r.service(), Severity.SEV3, "Customer ticket: " + r.subject(),
                    r.description() == null ? r.subject() : r.description(),
                    SignalAdapter.fingerprint("ticket", r.ticketId(), r.subject(), r.customerRef()),
                    at, attrs, null, null));
        };
    }

    @Bean
    SignalAdapter<GenericSignalRequest> genericAdapter() {
        return r -> {
            Instant at = r.occurredAt() == null ? Instant.now() : r.occurredAt();
            String fp = r.fingerprint() != null ? r.fingerprint()
                    : SignalAdapter.fingerprint("generic", r.kind(), r.source(), r.service(), r.title(), at);
            return List.of(new SignalPayload(UUID.randomUUID().toString(), r.kind(), r.source(), r.service(),
                    Severity.fromLabel(r.severity()), r.title(), r.description() == null ? r.title() : r.description(),
                    SignalAdapter.fingerprint("generic", fp), at,
                    r.attributes() == null ? Map.of() : r.attributes(), null, null));
        };
    }
}
