package com.democorp.warroom.agent.core;

import com.democorp.warroom.agent.AgentProperties;
import com.democorp.warroom.agent.agents.RemediationPlannerAgent;
import com.democorp.warroom.agent.comms.CommunicationAgent;
import com.democorp.warroom.agent.rca.RcaAgent;
import com.democorp.warroom.common.contracts.IncidentSnapshot;
import com.democorp.warroom.common.contracts.Investigation;
import com.democorp.warroom.common.contracts.Investigation.AgentFinding;
import com.democorp.warroom.common.contracts.Investigation.Evidence;
import com.democorp.warroom.common.contracts.Investigation.RcaResult;
import com.democorp.warroom.common.contracts.Investigation.RemediationProposal;
import com.democorp.warroom.common.contracts.SignalKind;
import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.common.events.EventPublisher;
import com.democorp.warroom.common.events.EventTypes;
import com.democorp.warroom.common.events.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Supervisor pattern + evidence board (Blackboard): agents never call each other. The supervisor
 * fans out phase-1 agents in parallel, collects their evidence, then runs RCA → remediation plan → comms.
 * A timed-out or failed agent is recorded as a gap; the investigation continues.
 */
@Service
public class Supervisor {

    private static final Logger log = LoggerFactory.getLogger(Supervisor.class);
    private static final String CHANGE_AGENT = "Change detection (incident-service)";

    private final List<EvidenceAgent> agents;
    private final RcaAgent rca;
    private final RemediationPlannerAgent planner;
    private final CommunicationAgent comms;
    private final InvestigationRepository investigations;
    private final EventPublisher publisher;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    private final AgentProperties props;
    private final MeterRegistry meters;

    public Supervisor(List<EvidenceAgent> agents, RcaAgent rca, RemediationPlannerAgent planner,
                      CommunicationAgent comms, InvestigationRepository investigations, EventPublisher publisher,
                      ObjectMapper mapper, ExecutorService agentExecutor, AgentProperties props, MeterRegistry meters) {
        this.agents = agents;
        this.rca = rca;
        this.planner = planner;
        this.comms = comms;
        this.investigations = investigations;
        this.publisher = publisher;
        this.mapper = mapper;
        this.executor = agentExecutor;
        this.props = props;
        this.meters = meters;
    }

    /** Records the investigation (idempotent per incident) and runs it in the background. */
    public void open(IncidentSnapshot incident) {
        if (investigations.findByIncidentId(incident.incidentId()).isPresent()) {
            return;
        }
        String id = "INV-" + incident.incidentId().replace("INC-", "");
        investigations.save(new InvestigationRecord(id, incident.incidentId(), json(incident)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Start only once the record is committed, so the background run can read it.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.submit(() -> run(id));
                }
            });
        } else {
            executor.submit(() -> run(id));
        }
    }

    /** Resume anything that was pending or running when the service stopped. */
    @EventListener(ApplicationReadyEvent.class)
    public void resume() {
        for (InvestigationRecord r : investigations.findByStatusInAndAttemptsLessThan(List.of("PENDING", "RUNNING"), 3)) {
            log.info("Resuming investigation {} for {}", r.getId(), r.getIncidentId());
            executor.submit(() -> run(r.getId()));
        }
    }

    void run(String investigationId) {
        InvestigationRecord record = investigations.findById(investigationId).orElseThrow();
        record.running();
        investigations.save(record);
        long start = System.currentTimeMillis();
        IncidentSnapshot incident = read(record.getSnapshot(), IncidentSnapshot.class);
        String incidentId = incident.incidentId();
        try {
            InvestigationContext ctx = new InvestigationContext(investigationId, incident,
                    incident.openedAt().minus(props.lookback()));
            List<String> names = new ArrayList<>(agents.stream().map(EvidenceAgent::name).toList());
            names.addAll(List.of(RcaAgent.NAME, RemediationPlannerAgent.NAME, CommunicationAgent.NAME));
            publish(incidentId, EventTypes.INVESTIGATION_STARTED,
                    new Investigation.Started(investigationId, incidentId, names, Instant.now()));

            List<Evidence> board = new ArrayList<>(changeEvidence(ctx));
            if (!board.isEmpty()) {
                publish(incidentId, EventTypes.AGENT_FINDING, new AgentFinding(investigationId, incidentId, CHANGE_AGENT,
                        "DONE", board.size() + " recent change(s) or ticket(s) linked", List.copyOf(board), 0));
            }

            // Phase 1: evidence agents in parallel, each with its own time limit
            List<String> gaps = new ArrayList<>();
            List<CompletableFuture<AgentFinding>> phase1 = agents.stream()
                    .map(agent -> CompletableFuture.supplyAsync(() -> runAgent(agent, ctx), executor)
                            .completeOnTimeout(new AgentFinding(investigationId, incidentId, agent.name(), "TIMED_OUT",
                                    "No answer within " + props.agentTimeout().toSeconds() + " s", List.of(),
                                    props.agentTimeout().toMillis()), props.agentTimeout().toMillis(), TimeUnit.MILLISECONDS))
                    .toList();
            for (CompletableFuture<AgentFinding> f : phase1) {
                AgentFinding finding = f.join();
                board.addAll(finding.evidence());
                if (!"DONE".equals(finding.status())) {
                    gaps.add(finding.agent() + " " + finding.status().toLowerCase().replace('_', ' ') + ": " + finding.summary());
                }
                publish(incidentId, EventTypes.AGENT_FINDING, finding);
            }

            // Phase 2: RCA (with knowledge search as a tool)
            long rcaStart = System.currentTimeMillis();
            List<Evidence> similar = rca.similarIncidents(ctx, board, gaps);
            board.addAll(similar);
            publish(incidentId, EventTypes.AGENT_FINDING, new AgentFinding(investigationId, incidentId, RcaAgent.NAME + " · knowledge search",
                    "DONE", similar.isEmpty() ? "No similar past incident found" : similar.size() + " similar past incident(s)",
                    similar, System.currentTimeMillis() - rcaStart));
            RcaResult result = rca.analyze(ctx, board, gaps);
            publish(incidentId, EventTypes.RCA_COMPLETED, result);

            // Phase 3: remediation plan, then communication drafts
            RemediationProposal plan = planner.plan(ctx, result, board);
            publish(incidentId, EventTypes.REMEDIATION_PROPOSED, plan);
            publish(incidentId, EventTypes.COMMS_DRAFTED, comms.afterInvestigation(investigationId, incident, result, plan, board));

            long took = System.currentTimeMillis() - start;
            publish(incidentId, EventTypes.INVESTIGATION_COMPLETED,
                    new Investigation.Completed(investigationId, incidentId, gaps.isEmpty() ? "COMPLETED" : "PARTIAL", gaps, took));
            record.finish("COMPLETED", json(Map.of("rca", result, "plan", plan, "evidence", board.size(), "gaps", gaps)));
            meters.timer("warroom.investigation.duration", "outcome", gaps.isEmpty() ? "completed" : "partial")
                    .record(took, TimeUnit.MILLISECONDS);
            log.info("Investigation {} for {} finished in {} s ({} evidence, {} gaps)", investigationId, incidentId,
                    took / 1000, board.size(), gaps.size());
        } catch (RuntimeException e) {
            log.error("Investigation {} failed", investigationId, e);
            record.finish("FAILED", json(Map.of("error", String.valueOf(e.getMessage()))));
            try {
                publish(incidentId, EventTypes.INVESTIGATION_COMPLETED, new Investigation.Completed(investigationId,
                        incidentId, "FAILED", List.of(String.valueOf(e.getMessage())), System.currentTimeMillis() - start));
            } catch (RuntimeException ignored) {
                // Kafka down too; the record is FAILED and can be retried.
            }
        }
        investigations.save(record);
    }

    private AgentFinding runAgent(EvidenceAgent agent, InvestigationContext ctx) {
        long t = System.currentTimeMillis();
        try {
            EvidenceAgent.Output out = agent.investigate(ctx);
            long took = System.currentTimeMillis() - t;
            meters.timer("warroom.agent.duration", "agent", agent.name(), "status", "done").record(took, TimeUnit.MILLISECONDS);
            return new AgentFinding(ctx.investigationId(), ctx.incident().incidentId(), agent.name(), "DONE",
                    out.summary(), out.evidence(), took);
        } catch (RuntimeException e) {
            log.warn("{} failed: {}", agent.name(), e.toString());
            meters.counter("warroom.agent.failures", "agent", agent.name()).increment();
            return new AgentFinding(ctx.investigationId(), ctx.incident().incidentId(), agent.name(), "FAILED",
                    "Tool error: " + e.getMessage(), List.of(), System.currentTimeMillis() - t);
        }
    }

    /** Deploys, config changes and tickets that incident-service already linked become evidence. */
    private List<Evidence> changeEvidence(InvestigationContext ctx) {
        List<Evidence> out = new ArrayList<>();
        for (SignalPayload s : ctx.incident().signals()) {
            Map<String, String> a = s.attributes() == null ? Map.of() : s.attributes();
            if (s.kind() == SignalKind.DEPLOY) {
                out.add(ctx.evidence(CHANGE_AGENT, "CHANGE", s.title(),
                        "Deployed by " + a.get("deployedBy") + " at " + s.occurredAt() + ". Change: " + s.description(),
                        "deploy event " + s.signalId(), List.of("recent_deploy:" + s.service() + ":" + a.get("toVersion"))));
            } else if (s.kind() == SignalKind.CONFIG_CHANGE) {
                out.add(ctx.evidence(CHANGE_AGENT, "CHANGE", s.title(),
                        "Changed by " + a.get("changedBy") + " at " + s.occurredAt() + ".",
                        "config change " + s.signalId(), List.of("config_change:" + s.service() + ":" + a.get("key"))));
            } else if (s.kind() == SignalKind.TICKET) {
                out.add(ctx.evidence(CHANGE_AGENT, "TICKET", s.title(), s.description(),
                        "ticket " + a.get("ticketId"), List.of("customer_ticket:" + s.service())));
            }
        }
        return out;
    }

    private void publish(String incidentId, String type, Object payload) {
        publisher.publish(Topics.INVESTIGATION, incidentId, type, incidentId, payload);
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
