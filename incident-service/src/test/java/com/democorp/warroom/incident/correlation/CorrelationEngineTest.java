package com.democorp.warroom.incident.correlation;

import com.democorp.warroom.common.catalog.ServiceCatalog;
import com.democorp.warroom.common.catalog.ServiceCatalog.ServiceInfo;
import com.democorp.warroom.common.contracts.Severity;
import com.democorp.warroom.common.contracts.SignalKind;
import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.incident.domain.Incident;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationEngineTest {

    private final ServiceCatalog catalog = new ServiceCatalog(List.of(
            new ServiceInfo("banking-api", "Digital", "priya", 1, List.of("payment-service", "account-service")),
            new ServiceInfo("payment-service", "Payments", "priya", 1, List.of("card-network")),
            new ServiceInfo("account-service", "Accounts", "priya", 1, List.of()),
            new ServiceInfo("card-network", "External", "priya", 2, List.of())));

    private final CorrelationEngine engine = new CorrelationEngine(List.of(
            new Rules.SameServiceRule(), new Rules.DependencyRule(catalog),
            new Rules.TimeProximityRule(), new Rules.OrphanTicketRule()),
            new CorrelationProperties(Duration.ofMinutes(15), Duration.ofMinutes(30), 0.6));

    private final Instant now = Instant.now();
    private final Incident paymentIncident = new Incident("INC-2041", "High error rate", "payment-service", Severity.SEV1, now);

    @Test
    void alertOnSameServiceJoinsTheIncident() {
        Optional<CorrelationEngine.Decision> d = engine.bestMatch(alert("payment-service"), List.of(paymentIncident));
        assertThat(d).isPresent();
        assertThat(d.get().reason()).contains("same service");
    }

    @Test
    void alertOnDependentServiceJoinsWhenRecent() {
        Optional<CorrelationEngine.Decision> d = engine.bestMatch(alert("banking-api"), List.of(paymentIncident));
        assertThat(d).isPresent();
        assertThat(d.get().reason()).contains("depend on each other").contains("within");
    }

    @Test
    void unrelatedServiceOpensItsOwnIncident() {
        Incident accountIncident = new Incident("INC-2042", "Cache down", "account-service", Severity.SEV2, now);
        assertThat(engine.bestMatch(alert("card-network"), List.of(accountIncident))).isEmpty();
    }

    @Test
    void ticketWithoutServiceJoinsOpenIncident() {
        SignalPayload ticket = signal(SignalKind.TICKET, null);
        assertThat(engine.bestMatch(ticket, List.of(paymentIncident))).isPresent();
    }

    private SignalPayload alert(String service) {
        return signal(SignalKind.ALERT, service);
    }

    private SignalPayload signal(SignalKind kind, String service) {
        return new SignalPayload("s-" + System.nanoTime(), kind, "test", service, Severity.SEV2, "title", "desc",
                "fp", now, Map.of(), null, 1);
    }
}
