package com.democorp.warroom.incident.correlation;

import com.democorp.warroom.common.catalog.ServiceCatalog;
import com.democorp.warroom.common.contracts.SignalKind;
import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.incident.domain.Incident;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** The built-in correlation rules. */
public final class Rules {

    private Rules() {
    }

    /** Same service as the incident: strongest signal. */
    @Component
    public static class SameServiceRule implements CorrelationRule {
        @Override
        public Optional<Match> evaluate(SignalPayload s, Incident incident) {
            if (s.service() != null && incident.getServices().contains(s.service())) {
                return Optional.of(new Match(0.6, "same service (" + s.service() + ")"));
            }
            return Optional.empty();
        }
    }

    /** One service calls the other (from the service catalog), e.g. banking-api → payment-service. */
    @Component
    public static class DependencyRule implements CorrelationRule {
        private final ServiceCatalog catalog;

        public DependencyRule(ServiceCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        public Optional<Match> evaluate(SignalPayload s, Incident incident) {
            if (s.service() == null || incident.getServices().contains(s.service())) {
                return Optional.empty();
            }
            return incident.getServices().stream()
                    .filter(existing -> catalog.related(existing, s.service()))
                    .findFirst()
                    .map(existing -> new Match(0.4, s.service() + " and " + existing + " depend on each other"));
        }
    }

    /** Arrived within 5 minutes of the incident's last activity. */
    @Component
    public static class TimeProximityRule implements CorrelationRule {
        @Override
        public Optional<Match> evaluate(SignalPayload s, Incident incident) {
            Instant at = s.occurredAt() == null ? Instant.now() : s.occurredAt();
            long gap = Math.abs(Duration.between(incident.getUpdatedAt(), at).toSeconds());
            if (gap <= 300) {
                return Optional.of(new Match(0.2, "within " + Math.max(1, gap / 60) + " min of the last signal"));
            }
            return Optional.empty();
        }
    }

    /** A customer ticket with no service named, during an open incident. */
    @Component
    public static class OrphanTicketRule implements CorrelationRule {
        @Override
        public Optional<Match> evaluate(SignalPayload s, Incident incident) {
            if (s.kind() == SignalKind.TICKET && s.service() == null) {
                return Optional.of(new Match(0.5, "customer ticket during an open incident"));
            }
            return Optional.empty();
        }
    }
}
