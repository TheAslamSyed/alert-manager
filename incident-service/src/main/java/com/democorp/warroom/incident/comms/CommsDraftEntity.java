package com.democorp.warroom.incident.comms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A stakeholder message draft. Nothing is sent until an approver presses send (FR-07). */
@Entity
@Table(name = "comms_draft")
public class CommsDraftEntity {

    public static final String DRAFT = "DRAFT";
    public static final String SENT = "SENT";
    public static final String SUPERSEDED = "SUPERSEDED";

    @Id
    private String id;
    @Column(name = "incident_id", nullable = false)
    private String incidentId;
    @Column(nullable = false)
    private String trigger;
    @Column(nullable = false)
    private String audience;
    @Column(nullable = false)
    private String channel;
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false)
    private String body;
    @Column(nullable = false)
    private String method;
    @Column(nullable = false)
    private String status;
    @Column(name = "edited_by")
    private String editedBy;
    @Column(name = "sent_by")
    private String sentBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;

    protected CommsDraftEntity() {
    }

    public CommsDraftEntity(String id, String incidentId, String trigger, String audience, String channel,
                            String subject, String body, String method) {
        this.id = id;
        this.incidentId = incidentId;
        this.trigger = trigger;
        this.audience = audience;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.method = method;
        this.status = DRAFT;
        this.createdAt = Instant.now();
    }

    public void edit(String subject, String body, String user) {
        requireDraft();
        if (subject != null && !subject.isBlank()) this.subject = subject;
        if (body != null && !body.isBlank()) this.body = body;
        this.editedBy = user;
    }

    public void send(String user) {
        requireDraft();
        this.status = SENT;
        this.sentBy = user;
        this.sentAt = Instant.now();
    }

    public void supersede() {
        if (DRAFT.equals(status)) {
            status = SUPERSEDED;
        }
    }

    private void requireDraft() {
        if (!DRAFT.equals(status)) {
            throw new IllegalStateException("Message " + id + " is " + status + " and can no longer change");
        }
    }

    public String getId() { return id; }
    public String getIncidentId() { return incidentId; }
    public String getTrigger() { return trigger; }
    public String getAudience() { return audience; }
    public String getChannel() { return channel; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getMethod() { return method; }
    public String getStatus() { return status; }
    public String getEditedBy() { return editedBy; }
    public String getSentBy() { return sentBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
