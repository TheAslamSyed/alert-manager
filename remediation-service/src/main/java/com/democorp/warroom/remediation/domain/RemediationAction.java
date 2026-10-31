package com.democorp.warroom.remediation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One proposed fix and its approval lifecycle (State pattern):
 * PROPOSED → PENDING_APPROVAL → APPROVED → EXECUTING → SUCCEEDED | FAILED_VERIFICATION | NO_EFFECT | ERROR,
 * or REJECTED. The safety rules live here so no code path can skip them.
 */
@Entity
@Table(name = "remediation_action")
public class RemediationAction {

    public enum Status { PROPOSED, PENDING_APPROVAL, APPROVED, EXECUTING, SUCCEEDED, FAILED_VERIFICATION, NO_EFFECT, ERROR, REJECTED }

    @Id
    private String id;
    @Column(name = "incident_id", nullable = false)
    private String incidentId;
    @Column(name = "investigation_id")
    private String investigationId;
    @Column(nullable = false)
    private String action;
    @Column(nullable = false)
    private String target;
    @Column(nullable = false)
    private String risk;
    @Column(name = "approvals_needed", nullable = false)
    private int approvalsNeeded;
    @Column(nullable = false)
    private String description;
    private String reason;
    @Column(nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;
    @Column(name = "proposed_by", nullable = false)
    private String proposedBy;
    @Column(name = "submitted_by")
    private String submittedBy;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> approvals = new ArrayList<>();
    @Column(name = "rejected_by")
    private String rejectedBy;
    @Column(name = "decision_reason")
    private String decisionReason;
    private String signature;
    private String outcome;
    @Column(name = "result_message")
    private String resultMessage;
    private String verification;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected RemediationAction() {
    }

    public RemediationAction(String id, String incidentId, String investigationId, String action, String target,
                             String risk, int approvalsNeeded, String description, String reason, String proposedBy) {
        this.id = id;
        this.incidentId = incidentId;
        this.investigationId = investigationId;
        this.action = action;
        this.target = target;
        this.risk = risk;
        this.approvalsNeeded = approvalsNeeded;
        this.description = description;
        this.reason = reason;
        this.proposedBy = proposedBy;
        this.status = Status.PROPOSED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    /** A responder reviews the agent's proposal and sends it for approval. */
    public void submit(String user) {
        require(status == Status.PROPOSED, "Only a proposed action can be sent for approval");
        submittedBy = user;
        move(Status.PENDING_APPROVAL);
    }

    /**
     * @return true when enough approvals are collected and the action may run
     */
    public boolean approve(String user, Set<String> roles, String why) {
        require(status == Status.PENDING_APPROVAL, "Action is not waiting for approval (status " + status + ")");
        require(roles.contains("APPROVER"), "Only an APPROVER can approve fixes");
        require(!roles.contains("ADMIN"), "Admins cannot approve fixes (separation of duties)");
        require(!user.equals(submittedBy), "You cannot approve an action you sent for approval");
        require(!approvals.contains(user), "You already approved this action; it needs a different second approver");
        List<String> copy = new ArrayList<>(approvals);
        copy.add(user);
        approvals = copy;
        decisionReason = why;
        updatedAt = Instant.now();
        if (approvals.size() >= approvalsNeeded) {
            move(Status.APPROVED);
            return true;
        }
        return false;
    }

    public void reject(String user, String why) {
        require(status == Status.PROPOSED || status == Status.PENDING_APPROVAL, "Action can no longer be rejected");
        rejectedBy = user;
        decisionReason = why;
        move(Status.REJECTED);
    }

    public void sign(String signature) {
        this.signature = signature;
    }

    public void startExecuting() {
        require(status == Status.APPROVED, "Only an approved action can run");
        move(Status.EXECUTING);
    }

    public void finish(Status result, String message, String verification) {
        require(status == Status.EXECUTING, "Action is not running");
        this.outcome = result.name();
        this.resultMessage = message;
        this.verification = verification;
        move(result);
    }

    private void move(Status next) {
        status = next;
        updatedAt = Instant.now();
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new ApprovalRuleException(message);
        }
    }

    public static class ApprovalRuleException extends RuntimeException {
        public ApprovalRuleException(String message) {
            super(message);
        }
    }

    public String getId() { return id; }
    public String getIncidentId() { return incidentId; }
    public String getInvestigationId() { return investigationId; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getRisk() { return risk; }
    public int getApprovalsNeeded() { return approvalsNeeded; }
    public String getDescription() { return description; }
    public String getReason() { return reason; }
    public Status getStatus() { return status; }
    public String getProposedBy() { return proposedBy; }
    public String getSubmittedBy() { return submittedBy; }
    public List<String> getApprovals() { return List.copyOf(approvals); }
    public String getRejectedBy() { return rejectedBy; }
    public String getDecisionReason() { return decisionReason; }
    public String getSignature() { return signature; }
    public String getOutcome() { return outcome; }
    public String getResultMessage() { return resultMessage; }
    public String getVerification() { return verification; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
