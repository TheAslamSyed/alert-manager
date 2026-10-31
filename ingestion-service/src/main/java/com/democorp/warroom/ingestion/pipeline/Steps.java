package com.democorp.warroom.ingestion.pipeline;

import com.democorp.warroom.common.catalog.ServiceCatalog;
import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.ingestion.IngestProperties;
import com.democorp.warroom.ingestion.store.SignalFingerprintRepository;
import com.democorp.warroom.ingestion.store.SignalFingerprint;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/** The three pipeline steps: validate → drop duplicates → enrich from the service catalog. */
public final class Steps {

    private Steps() {
    }

    @Component
    @Order(10)
    public static class ValidationStep implements SignalStep {
        @Override
        public Optional<SignalPayload> apply(SignalPayload s) {
            if (s.kind() == null || s.title() == null || s.title().isBlank()) {
                throw new InvalidSignalException("Signal needs a kind and a title");
            }
            if (s.occurredAt() != null && s.occurredAt().isAfter(Instant.now().plusSeconds(300))) {
                throw new InvalidSignalException("occurredAt is in the future: " + s.occurredAt());
            }
            String service = s.service() == null || s.service().isBlank() ? null
                    : s.service().trim().toLowerCase(Locale.ROOT);
            String title = s.title().length() > 300 ? s.title().substring(0, 300) : s.title();
            String description = s.description() != null && s.description().length() > 4000
                    ? s.description().substring(0, 4000) : s.description();
            return Optional.of(new SignalPayload(s.signalId(), s.kind(), s.source(), service, s.severity(), title,
                    description, s.fingerprint(), s.occurredAt() == null ? Instant.now() : s.occurredAt(),
                    s.attributes(), s.team(), s.tier()));
        }
    }

    @Component
    @Order(20)
    public static class DedupStep implements SignalStep {
        private final SignalFingerprintRepository fingerprints;
        private final IngestProperties props;

        public DedupStep(SignalFingerprintRepository fingerprints, IngestProperties props) {
            this.fingerprints = fingerprints;
            this.props = props;
        }

        @Override
        public Optional<SignalPayload> apply(SignalPayload s) {
            Instant now = Instant.now();
            Optional<SignalFingerprint> existing = fingerprints.findForUpdate(s.fingerprint());
            if (existing.isEmpty()) {
                fingerprints.save(new SignalFingerprint(s.fingerprint(), now));
                return Optional.of(s);
            }
            SignalFingerprint fp = existing.get();
            if (fp.getLastSeen().isAfter(now.minus(props.dedupWindow()))) {
                fp.seenAgain(now);
                return Optional.empty();
            }
            fp.restart(now);
            return Optional.of(s);
        }
    }

    @Component
    @Order(30)
    public static class EnrichmentStep implements SignalStep {
        private final ServiceCatalog catalog;

        public EnrichmentStep(ServiceCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        public Optional<SignalPayload> apply(SignalPayload s) {
            return Optional.of(catalog.find(s.service())
                    .map(info -> s.withEnrichment(info.team(), info.tier()))
                    .orElse(s));
        }
    }
}
