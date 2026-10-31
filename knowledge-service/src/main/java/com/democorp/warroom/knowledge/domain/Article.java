package com.democorp.warroom.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A past incident, runbook or postmortem. Only PUBLISHED articles are searchable. */
@Entity
@Table(name = "kb_article")
public class Article {

    public static final String PUBLISHED = "PUBLISHED";
    public static final String IN_REVIEW = "IN_REVIEW";

    @Id
    private String id;
    @Column(nullable = false)
    private String kind;
    @Column(nullable = false)
    private String ref;
    @Column(nullable = false)
    private String title;
    private String service;
    @Column(nullable = false)
    private String summary;
    @Column(name = "root_cause")
    private String rootCause;
    @Column(name = "resolution_action")
    private String resolutionAction;
    private String resolution;
    @Column(name = "mttr_minutes")
    private Integer mttrMinutes;
    private String body;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private boolean indexed;
    @Column(name = "occurred_at")
    private Instant occurredAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "reviewed_by")
    private String reviewedBy;

    protected Article() {
    }

    public Article(String id, String kind, String ref, String title, String service, String summary, String rootCause,
                   String resolutionAction, String resolution, Integer mttrMinutes, String body, String status,
                   Instant occurredAt) {
        this.id = id;
        this.kind = kind;
        this.ref = ref;
        this.title = title;
        this.service = service;
        this.summary = summary;
        this.rootCause = rootCause;
        this.resolutionAction = resolutionAction;
        this.resolution = resolution;
        this.mttrMinutes = mttrMinutes;
        this.body = body;
        this.status = status;
        this.occurredAt = occurredAt;
        this.createdAt = Instant.now();
        if (PUBLISHED.equals(status)) {
            this.publishedAt = this.createdAt;
        }
    }

    public void edit(String summary, String rootCause, String resolution, String body) {
        if (PUBLISHED.equals(status)) {
            throw new IllegalStateException("Published articles are read-only");
        }
        if (summary != null) this.summary = summary;
        if (rootCause != null) this.rootCause = rootCause;
        if (resolution != null) this.resolution = resolution;
        if (body != null) this.body = body;
    }

    public void publish(String reviewer) {
        if (PUBLISHED.equals(status)) {
            throw new IllegalStateException("Article " + id + " is already published");
        }
        status = PUBLISHED;
        reviewedBy = reviewer;
        publishedAt = Instant.now();
        indexed = false;
    }

    public void markIndexed() {
        indexed = true;
    }

    /** Text used for the embedding. */
    public String embeddingText() {
        return title + "\n" + summary + "\nRoot cause: " + (rootCause == null ? "" : rootCause)
                + "\nResolution: " + (resolution == null ? "" : resolution);
    }

    public String getId() { return id; }
    public String getKind() { return kind; }
    public String getRef() { return ref; }
    public String getTitle() { return title; }
    public String getService() { return service; }
    public String getSummary() { return summary; }
    public String getRootCause() { return rootCause; }
    public String getResolutionAction() { return resolutionAction; }
    public String getResolution() { return resolution; }
    public Integer getMttrMinutes() { return mttrMinutes; }
    public String getBody() { return body; }
    public String getStatus() { return status; }
    public boolean isIndexed() { return indexed; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getReviewedBy() { return reviewedBy; }
}
