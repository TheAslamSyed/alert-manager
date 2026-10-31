package com.democorp.warroom.ingestion.api;

import com.democorp.warroom.ingestion.adapter.AlertmanagerAdapter;
import com.democorp.warroom.ingestion.adapter.SignalAdapter;
import com.democorp.warroom.ingestion.api.IngestRequests.AlertmanagerWebhook;
import com.democorp.warroom.ingestion.api.IngestRequests.ConfigChangeRequest;
import com.democorp.warroom.ingestion.api.IngestRequests.DeployRequest;
import com.democorp.warroom.ingestion.api.IngestRequests.GenericSignalRequest;
import com.democorp.warroom.ingestion.api.IngestRequests.IngestResponse;
import com.democorp.warroom.ingestion.api.IngestRequests.TicketRequest;
import com.democorp.warroom.ingestion.pipeline.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-01: one webhook per kind of source. All return 202 Accepted quickly. */
@RestController
@RequestMapping("/ingest")
@Tag(name = "Ingestion", description = "Webhooks for alerts, deploys, config changes and tickets (X-API-Key)")
public class IngestController {

    private final IngestionService ingestion;
    private final AlertmanagerAdapter alertmanager;
    private final SignalAdapter<DeployRequest> deploys;
    private final SignalAdapter<ConfigChangeRequest> configChanges;
    private final SignalAdapter<TicketRequest> tickets;
    private final SignalAdapter<GenericSignalRequest> generic;

    public IngestController(IngestionService ingestion, AlertmanagerAdapter alertmanager,
                            SignalAdapter<DeployRequest> deploys, SignalAdapter<ConfigChangeRequest> configChanges,
                            SignalAdapter<TicketRequest> tickets, SignalAdapter<GenericSignalRequest> generic) {
        this.ingestion = ingestion;
        this.alertmanager = alertmanager;
        this.deploys = deploys;
        this.configChanges = configChanges;
        this.tickets = tickets;
        this.generic = generic;
    }

    @PostMapping("/alertmanager")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Prometheus Alertmanager webhook")
    public IngestResponse alertmanager(@Valid @RequestBody AlertmanagerWebhook body) {
        return ingestion.ingest(alertmanager.toSignals(body));
    }

    @PostMapping("/deploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "A release went out (CI/CD)")
    public IngestResponse deploy(@Valid @RequestBody DeployRequest body) {
        return ingestion.ingest(deploys.toSignals(body));
    }

    @PostMapping("/config-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "A configuration value changed")
    public IngestResponse configChange(@Valid @RequestBody ConfigChangeRequest body) {
        return ingestion.ingest(configChanges.toSignals(body));
    }

    @PostMapping("/ticket")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Customer / support ticket")
    public IngestResponse ticket(@Valid @RequestBody TicketRequest body) {
        return ingestion.ingest(tickets.toSignals(body));
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Generic signal, e.g. a log-based alert")
    public IngestResponse event(@Valid @RequestBody GenericSignalRequest body) {
        return ingestion.ingest(generic.toSignals(body));
    }
}
