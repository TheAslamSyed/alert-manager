package com.democorp.warroom.remediation.service;

import com.democorp.warroom.common.contracts.Investigation.ProposedAction;
import com.democorp.warroom.common.contracts.Investigation.RemediationProposal;
import com.democorp.warroom.common.contracts.Remediation.Decision;
import com.democorp.warroom.common.contracts.Remediation.Result;
import com.democorp.warroom.common.events.EventPublisher;
import com.democorp.warroom.common.events.EventTypes;
import com.democorp.warroom.common.events.Topics;
import com.democorp.warroom.remediation.catalog.ActionCatalog;
import com.democorp.warroom.remediation.catalog.ActionCatalog.ActionSpec;
import com.democorp.warroom.remediation.domain.RemediationAction;
import com.democorp.warroom.remediation.domain.RemediationAction.Status;
import com.democorp.warroom.remediation.domain.RemediationActionRepository;
import com.democorp.warroom.remediation.domain.RemediationPlan;
import com.democorp.warroom.remediation.domain.RemediationPlanRepository;
import com.democorp.warroom.remediation.execution.ApprovalSigner;
import com.democorp.warroom.remediation.execution.DemocorpCommandFactory;
import com.democorp.warroom.remediation.execution.RemediationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/** FR-06 / FR-08: proposals in, approvals by humans, signed command out, executor runs and verifies. */
@Service
public class RemediationService {

    private static final Logger log = LoggerFactory.getLogger(RemediationService.class);

    private final RemediationActionRepository actions;
    private final RemediationPlanRepository plans;
    private final ActionCatalog catalog;
    private final ApprovalSigner signer;
    private final DemocorpCommandFactory commands;
    private final EventPublisher publisher;

    public RemediationService(RemediationActionRepository actions, RemediationPlanRepository plans,
                              ActionCatalog catalog, ApprovalSigner signer, DemocorpCommandFactory commands,
                              EventPublisher publisher) {
        this.actions = actions;
        this.plans = plans;
        this.catalog = catalog;
        this.signer = signer;
        this.commands = commands;
        this.publisher = publisher;
    }

    /** The planner's proposal becomes PROPOSED actions. Anything not on the allowlist is dropped. */
    @Transactional
    public void onProposal(RemediationProposal p) {
        if (plans.existsById(p.incidentId())) {
            return;
        }
        plans.save(new RemediationPlan(p.incidentId(), p.rootCause(), p.preventive(), p.closeAsFalsePositive(), p.rationale()));
        for (ProposedAction a : p.corrective()) {
            catalog.find(a.action()).ifPresentOrElse(spec -> {
                String id = "ACT-" + actions.nextNumber();
                actions.save(new RemediationAction(id, p.incidentId(), p.investigationId(), spec.action(), a.target(),
                        spec.risk(), spec.approvalsNeeded(), spec.description(), a.reason(), "Remediation planner"));
                log.info("{} proposed for {}: {} on {} ({} risk)", id, p.incidentId(), spec.action(), a.target(), spec.risk());
            }, () -> log.warn("Dropped non-allow-listed action {} for {}", a.action(), p.incidentId()));
        }
    }

    @Transactional
    public RemediationAction submit(String id, String user) {
        RemediationAction a = find(id);
        a.submit(user);
        return actions.save(a);
    }

    @Transactional
    public RemediationAction approve(String id, String user, Set<String> roles, String reason) {
        RemediationAction a = find(id);
        boolean ready = a.approve(user, roles, reason);
        if (ready) {
            String signature = signer.sign(a.getId(), a.getAction(), a.getTarget(), a.getApprovals());
            a.sign(signature);
            publisher.publish(Topics.REMEDIATION, a.getIncidentId(), EventTypes.ACTION_APPROVED, a.getIncidentId(),
                    new Decision(a.getId(), a.getIncidentId(), a.getAction(), a.getTarget(), a.getRisk(), "APPROVED",
                            user, reason, a.getApprovals(), signature));
            log.info("{} approved by {} — signed command sent to executor", a.getId(), a.getApprovals());
        }
        return actions.save(a);
    }

    @Transactional
    public RemediationAction reject(String id, String user, String reason) {
        RemediationAction a = find(id);
        a.reject(user, reason);
        publisher.publish(Topics.REMEDIATION, a.getIncidentId(), EventTypes.ACTION_REJECTED, a.getIncidentId(),
                new Decision(a.getId(), a.getIncidentId(), a.getAction(), a.getTarget(), a.getRisk(), "REJECTED",
                        user, reason, a.getApprovals(), null));
        return actions.save(a);
    }

    public String dryRun(String id) {
        RemediationAction a = find(id);
        ActionSpec spec = catalog.find(a.getAction()).orElseThrow();
        return commands.create(spec, a.getTarget(), "dry-run").dryRun();
    }

    /** Executor: runs only signed, approved commands. */
    public void execute(Decision decision) {
        RemediationAction a = startIfValid(decision);
        if (a == null) {
            return;
        }
        ActionSpec spec = catalog.find(a.getAction()).orElseThrow();
        RemediationCommand cmd = commands.create(spec, a.getTarget(), String.join(" + ", a.getApprovals()));
        Status status;
        String message;
        String verification = null;
        try {
            cmd.checkPreconditions();
            String preview = cmd.dryRun();
            RemediationCommand.Outcome outcome = cmd.execute();
            if (!outcome.effective()) {
                status = Status.NO_EFFECT;
                message = outcome.message() + " " + cmd.undo();
            } else {
                RemediationCommand.Verification v = cmd.verify();
                verification = v.detail();
                status = v.passed() ? Status.SUCCEEDED : Status.FAILED_VERIFICATION;
                message = v.passed() ? outcome.message() + " Verified." : outcome.message() + " " + cmd.undo();
            }
            log.info("{} {} → {} (dry run said: {})", a.getId(), a.getAction(), status, preview);
        } catch (RuntimeException e) {
            status = Status.ERROR;
            message = "Executor error: " + e.getMessage();
            log.error("{} failed", a.getId(), e);
        }
        finish(a.getId(), status, message, verification);
    }

    // Each save is its own transaction; @Version on the entity stops two executors running the same action.
    RemediationAction startIfValid(Decision d) {
        RemediationAction a = actions.findById(d.actionId()).orElse(null);
        if (a == null || a.getStatus() != Status.APPROVED) {
            return null;
        }
        if (!signer.verify(d.signature(), a.getId(), a.getAction(), a.getTarget(), a.getApprovals())
                || !d.signature().equals(a.getSignature())) {
            log.error("Refusing {}: approval signature is invalid", d.actionId());
            return null;
        }
        a.startExecuting();
        return actions.save(a);
    }

    void finish(String id, Status status, String message, String verification) {
        RemediationAction a = find(id);
        a.finish(status, message, verification);
        actions.save(a);
        String type = status == Status.SUCCEEDED ? EventTypes.ACTION_EXECUTED : EventTypes.ACTION_FAILED;
        publisher.publish(Topics.REMEDIATION, a.getIncidentId(), type, a.getIncidentId(),
                new Result(a.getId(), a.getIncidentId(), a.getAction(), a.getTarget(), status.name(),
                        status == Status.SUCCEEDED, message, verification));
    }

    public List<RemediationAction> list(String incidentId, Status status) {
        if (incidentId != null) {
            return actions.findByIncidentIdOrderByCreatedAtAsc(incidentId);
        }
        if (status != null) {
            return actions.findByStatusOrderByCreatedAtAsc(status);
        }
        return actions.findAllByOrderByCreatedAtDesc();
    }

    public RemediationAction find(String id) {
        return actions.findById(id).orElseThrow(() -> new NoSuchElementException("No action " + id));
    }

    public RemediationPlan plan(String incidentId) {
        return plans.findById(incidentId).orElseThrow(() -> new NoSuchElementException("No plan for " + incidentId));
    }
}
