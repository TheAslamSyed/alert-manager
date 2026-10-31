package com.democorp.warroom.ingestion.pipeline;

import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.common.events.EventPublisher;
import com.democorp.warroom.common.events.EventTypes;
import com.democorp.warroom.common.events.Topics;
import com.democorp.warroom.ingestion.api.IngestRequests.IngestResponse;
import com.democorp.warroom.ingestion.store.RawSignal;
import com.democorp.warroom.ingestion.store.RawSignalRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Runs every signal through the step chain, stores it, and publishes accepted ones to signals.v1. */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final List<SignalStep> steps;
    private final RawSignalRepository rawSignals;
    private final EventPublisher publisher;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    public IngestionService(List<SignalStep> steps, RawSignalRepository rawSignals, EventPublisher publisher,
                            ObjectMapper mapper, MeterRegistry meters) {
        this.steps = steps;
        this.rawSignals = rawSignals;
        this.publisher = publisher;
        this.mapper = mapper;
        this.meters = meters;
    }

    @Transactional
    public IngestResponse ingest(List<SignalPayload> signals) {
        List<String> accepted = new ArrayList<>();
        int duplicates = 0;
        for (SignalPayload incoming : signals) {
            Optional<SignalPayload> result = Optional.of(incoming);
            for (SignalStep step : steps) {
                result = result.flatMap(step::apply);
                if (result.isEmpty()) {
                    break;
                }
            }
            boolean duplicate = result.isEmpty();
            rawSignals.save(new RawSignal(incoming.signalId(), incoming.kind().name(), incoming.source(),
                    incoming.service(), incoming.fingerprint(), duplicate, toJson(incoming), Instant.now()));
            meters.counter("warroom.signals.received", "kind", incoming.kind().name(),
                    "outcome", duplicate ? "duplicate" : "accepted").increment();
            if (duplicate) {
                duplicates++;
                continue;
            }
            SignalPayload signal = result.get();
            String key = signal.service() == null ? "unknown" : signal.service();
            // Sent inside the transaction: if Kafka is down the request fails and rolls back, so the
            // sender retries (at-least-once). Downstream consumers are idempotent.
            publisher.publish(Topics.SIGNALS, key, EventTypes.SIGNAL_RECEIVED, null, signal);
            accepted.add(signal.signalId());
            log.info("Accepted {} signal from {} for {}: {}", signal.kind(), signal.source(), key, signal.title());
        }
        return new IngestResponse(signals.size(), accepted.size(), duplicates, accepted);
    }

    private String toJson(SignalPayload signal) {
        try {
            return mapper.writeValueAsString(signal);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise signal", e);
        }
    }
}
