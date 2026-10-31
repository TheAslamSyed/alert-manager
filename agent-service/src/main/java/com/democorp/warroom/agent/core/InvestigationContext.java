package com.democorp.warroom.agent.core;

import com.democorp.warroom.common.contracts.IncidentSnapshot;
import com.democorp.warroom.common.contracts.Investigation.Evidence;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** What every agent gets: the incident, the time window to look at, and an evidence-id counter. */
public final class InvestigationContext {

    private final String investigationId;
    private final IncidentSnapshot incident;
    private final Instant from;
    private final AtomicInteger evidenceCounter = new AtomicInteger();

    public InvestigationContext(String investigationId, IncidentSnapshot incident, Instant from) {
        this.investigationId = investigationId;
        this.incident = incident;
        this.from = from;
    }

    public Evidence evidence(String agent, String source, String title, String detail, String query, List<String> tags) {
        return new Evidence("E-" + evidenceCounter.incrementAndGet(), agent, source, title, detail, query,
                Instant.now(), tags);
    }

    public String investigationId() { return investigationId; }
    public IncidentSnapshot incident() { return incident; }
    public Instant from() { return from; }
    public List<String> services() { return incident.services(); }
}
