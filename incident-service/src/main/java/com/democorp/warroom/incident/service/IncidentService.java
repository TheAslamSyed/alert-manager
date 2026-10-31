package com.democorp.warroom.incident.service;

import com.democorp.warroom.common.catalog.ServiceCatalog;
import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.common.contracts.Severity;
import com.democorp.warroom.common.contracts.SignalKind;
import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.common.events.EventCodec;
import com.democorp.warroom.common.events.EventEnvelope;
import com.democorp.warroom.common.events.EventPublisher;
import com.democorp.warroom.common.events.EventTypes;
import com.democorp.warroom.common.events.Topics;
import com.democorp.warroom.common.messaging.IdempotencyGuard;
import com.democorp.warroom.incident.api.IncidentViews.IncidentDetail;
import com.democorp.warroom.incident.api.IncidentViews.IncidentSummary;
import com.democorp.warroom.incident.correlation.CorrelationEngine;
import com.democorp.warroom.incident.correlation.CorrelationEngine.Decision;
import com.democorp.warroom.incident.correlation.CorrelationProperties;
import com.democorp.warroom.incident.domain.Incident;
import com.democorp.warroom.incident.domain.IncidentRepository;
import com.democorp.warroom.incident.domain.IncidentSignal;
import com.democorp.warroom.incident.domain.IncidentSignalRepository;
import com.democorp.warroom.incident.domain.TimelineEntry;
import com.democorp.warroom.incident.domain.TimelineRepository;
import com.democorp.warroom.incident.live.IncidentStream;
import com.democorp.warroom.incident.live.IncidentStream.LiveEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * FR-02: turns the signal stream into incidents.
 * <ul>
 *   <li>Alerts either join the best-matching open incident or open a new one.</li>
 *   <li>Deploys, config changes and tickets are context: they join an open incident, or wait
 *       (up to the look-back window) and are attached when one opens. This is the change detection.</li>
 * </ul>
 * Events are published inside the transaction (at-least-once); consumers are idempotent.
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
    private static final String SYSTEM = "war-room";
    private static final Set<IncidentStatus> CLOSED = EnumSet.of(IncidentStatus.RESOLVED, IncidentStatus.CLOSED_FALSE_POSITIVE);
    private static final long CORRELATION_LOCK = 4242L;

    private final IncidentRepository incidents;
    private final IncidentSignalRepository signals;
    private final TimelineRepository timeline;
    private final CorrelationEngine engine;
    private final CorrelationProperties props;
    private final ServiceCatalog catalog;
    private final IncidentMapper mapper;
    private final EventPublisher publisher;
    private final EventCodec codec;
    private final IdempotencyGuard guard;
    private final JdbcTemplate jdbc;
    private final IncidentStream stream;
    private final MeterRegistry meters;

    public IncidentService(IncidentRepository incidents, IncidentSignalRepository signals, TimelineRepository timeline,
                           CorrelationEngine engine, CorrelationProperties props, ServiceCatalog catalog,
                           IncidentMapper mapper, EventPublisher publisher, EventCodec codec, IdempotencyGuard guard,
                           JdbcTemplate jdbc, IncidentStream stream, MeterRegistry meters) {
        this.incidents = incidents;
        this.signals = signals;
        this.timeline = timeline;
        this.engine = engine;
        this.props = props;
        this.catalog = catalog;
        this.mapper = mapper;
        this.publisher = publisher;
        this.codec = codec;
        this.guard = guard;
        this.jdbc = jdbc;
        this.stream = stream;
        this.meters = meters;
    }

    // ------------------------------------------------------------------ signals

    @Transactional
    public void onSignalEvent(EventEnvelope envelope) {
        if (!EventTypes.SIGNAL_RECEIVED.equals(envelope.type())) {
            return;
        }
        if (!guard.firstTime(envelope.messageId(), "incident.signals")) {
            log.debug("Duplicate signal event {} ignored", envelope.messageId());
            return;
        }
        SignalPayload s = codec.payload(envelope, SignalPayload.class);
        // One correlation decision at a time, even with several service instances.
        jdbc.execute("SELECT pg_advisory_xact_lock(" + CORRELATION_LOCK + ")");
        if (signals.existsById(s.signalId())) {
            return;
        }
        Instant now = Instant.now();
        IncidentSignal stored = new IncidentSignal(s.signalId(), s.kind(), s.service(), s.title(),
                s.occurredAt() == null ? now : s.occurredAt(), now, mapper.json(s));

        List<Incident> open = incidents.findByStatusNotInAndUpdatedAtAfterOrderByUpdatedAtDesc(
                CLOSED, now.minus(props.window()));
        Optional<Decision> match = engine.bestMatch(s, open);

        if (match.isPresent()) {
            attach(match.get().incident(), stored, s, match.get().reason(), now);
        } else if (opensIncident(s)) {
            open(stored, s, now);
        } else {
            signals.save(stored);
            log.info("Holding {} signal '{}' as context until an incident opens", s.kind(), s.title());
        }
    }

    private void attach(Incident incident, IncidentSignal stored, SignalPayload s, String reason, Instant now) {
        stored.linkTo(incident.getId(), reason);
        signals.save(stored);
        incident.addService(s.service());
        String text;
        if (isResolvedAlert(s)) {
            text = "Alert resolved: " + s.title().replaceFirst("^Resolved: ", "");
        } else if (isChange(s)) {
            String note = changeNote(s, incident.getOpenedAt());
            incident.addNote(note);
            text = "Change linked: " + note;
        } else if (s.kind() == SignalKind.TICKET) {
            text = changeNote(s, incident.getOpenedAt());
        } else {
            incident.raiseSeverity(effectiveSeverity(s));
            text = "Alert linked: " + s.title() + " (" + reason + ")";
        }
        incident.touch(now);
        incidents.save(incident);
        addTimeline(incident.getId(), now, "SIGNAL_LINKED", text, SYSTEM);
        publishSnapshot(incident, EventTypes.INCIDENT_UPDATED, null, SYSTEM);
        pushAfterCommit("updated", incident, text);
        log.info("Linked {} '{}' to {} ({})", s.kind(), s.title(), incident.getId(), reason);
    }

    private void open(IncidentSignal stored, SignalPayload s, Instant now) {
        String id = "INC-" + incidents.nextNumber();
        Incident incident = new Incident(id, s.title(), s.service(), effectiveSeverity(s), now);
        incident.addNote("Opened by an alert from " + s.source() + " on " + s.service());
        incidents.save(incident);
        stored.linkTo(id, "first alert");
        signals.save(stored);
        addTimeline(id, now, "OPENED", "Incident opened from alert: " + s.title(), SYSTEM);

        Instant reference = s.occurredAt() == null ? now : s.occurredAt();
        for (IncidentSignal pending : signals.findByIncidentIdIsNullAndReceivedAtAfterOrderByOccurredAtAsc(
                now.minus(props.contextLookback()))) {
            SignalPayload ps = mapper.payload(pending);
            boolean relevant = ps.service() == null || catalog.related(ps.service(), s.service());
            if (!relevant) {
                continue;
            }
            String note = changeNote(ps, reference);
            pending.linkTo(id, isChange(ps) ? "recent change on " + ps.service() : "customer ticket");
            signals.save(pending);
            if (isChange(ps)) {
                incident.addService(ps.service());
                incident.addNote(note);
            }
            addTimeline(id, ps.occurredAt() == null ? now : ps.occurredAt(),
                    isChange(ps) ? "CHANGE" : "TICKET", note, SYSTEM);
        }
        incidents.save(incident);
        meters.counter("warroom.incidents.opened", "severity", incident.getSeverity().name()).increment();
        publishSnapshot(incident, EventTypes.INCIDENT_OPENED, null, SYSTEM);
        pushAfterCommit("opened", incident, "Incident opened: " + s.title());
        log.info("Opened {} ({}) for {}: {}", id, incident.getSeverity(), s.service(), s.title());
    }

    // ------------------------------------------------------------------ lifecycle

    @Transactional
    public IncidentDetail changeStatus(String id, IncidentStatus target, String actor, String note) {
        Incident incident = find(id);
        Instant now = Instant.now();
        IncidentStatus previous = incident.moveTo(target, now);
        incidents.save(incident);
        String text = label(previous) + " → " + label(target) + (note == null || note.isBlank() ? "" : ": " + note);
        addTimeline(id, now, "STATUS", text, actor);
        publishSnapshot(incident, EventTypes.INCIDENT_STATUS_CHANGED, previous, actor);
        pushAfterCommit("status", incident, text);
        return detail(id);
    }

    /** For automatic moves driven by other services: skips the move if it is not allowed right now. */
    @Transactional
    public boolean moveIfAllowed(String id, IncidentStatus target, String actor, String note) {
        Optional<Incident> found = incidents.findById(id);
        if (found.isEmpty() || !found.get().getStatus().canMoveTo(target)) {
            return false;
        }
        changeStatus(id, target, actor, note);
        return true;
    }

    @Transactional
    public void addTimelineEntry(String id, String type, String text, String actor) {
        Incident incident = find(id);
        Instant now = Instant.now();
        incident.touch(now);
        incidents.save(incident);
        addTimeline(id, now, type, text, actor);
        pushAfterCommit("timeline", incident, text);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<IncidentSummary> list(IncidentStatus status, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        List<Incident> found = status == null ? incidents.findAllByOrderByOpenedAtDesc(page)
                : incidents.findByStatusOrderByOpenedAtDesc(status, page);
        return found.stream().map(i -> mapper.summary(i, signals.countByIncidentId(i.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public IncidentDetail detail(String id) {
        Incident incident = find(id);
        return mapper.detail(incident, signals.findByIncidentIdOrderByOccurredAtAsc(id),
                timeline.findByIncidentIdOrderByAtAscIdAsc(id));
    }

    public Incident find(String id) {
        return incidents.findById(id).orElseThrow(() -> new NoSuchElementException("No incident " + id));
    }

    // ------------------------------------------------------------------ helpers

    public void publishSnapshot(Incident incident, String type, IncidentStatus previous, String actor) {
        publisher.publish(Topics.INCIDENT, incident.getId(), type, incident.getId(),
                mapper.snapshot(incident, signals.findByIncidentIdOrderByOccurredAtAsc(incident.getId()), previous, actor));
    }

    public void pushAfterCommit(String type, Incident incident, String message) {
        LiveEvent event = new LiveEvent(type, incident.getId(), incident.getStatus().name(),
                incident.getSeverity().name(), incident.getTitle(), message, Instant.now());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stream.broadcast(event);
                }
            });
        } else {
            stream.broadcast(event);
        }
    }

    private void addTimeline(String incidentId, Instant at, String type, String text, String actor) {
        timeline.save(new TimelineEntry(incidentId, at, type, text, actor));
    }

    private Severity effectiveSeverity(SignalPayload s) {
        Severity severity = s.severity() == null ? Severity.SEV3 : s.severity();
        return Integer.valueOf(1).equals(s.tier()) ? severity.moreSevere(Severity.SEV2) : severity;
    }

    private static boolean opensIncident(SignalPayload s) {
        return s.kind().opensIncident() && !isResolvedAlert(s);
    }

    private static boolean isResolvedAlert(SignalPayload s) {
        return s.attributes() != null && "resolved".equals(s.attributes().get("alertStatus"));
    }

    private static boolean isChange(SignalPayload s) {
        return s.kind() == SignalKind.DEPLOY || s.kind() == SignalKind.CONFIG_CHANGE;
    }

    /** Human-readable change-detection note, e.g. "payment-service deployed 2.2.0 → 2.3.0, 2 min before the first alert". */
    static String changeNote(SignalPayload s, Instant reference) {
        Instant at = s.occurredAt() == null ? Instant.now() : s.occurredAt();
        long secs = Duration.between(at, reference).toSeconds();
        String when = secs >= 120 ? (secs / 60) + " min before the first alert"
                : secs >= 0 ? secs + " s before the first alert"
                : Math.max(1, -secs / 60) + " min after the incident opened";
        Map<String, String> a = s.attributes() == null ? Map.of() : s.attributes();
        return switch (s.kind()) {
            case DEPLOY -> s.service() + " deployed " + a.get("fromVersion") + " → " + a.get("toVersion") + ", " + when;
            case CONFIG_CHANGE -> s.service() + " config " + a.get("key") + " changed " + a.get("oldValue")
                    + " → " + a.get("newValue") + ", " + when;
            case TICKET -> s.title() + ", " + when;
            default -> s.title();
        };
    }

    private static String label(IncidentStatus status) {
        String s = status.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
