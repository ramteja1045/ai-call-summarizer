package com.ramteja.calls.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calls")
public class Call {

    public enum Status { PENDING, ANALYZED, FAILED }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String agentName;

    @Column(nullable = false, columnDefinition = "text")
    private String transcript;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(columnDefinition = "text")
    private String summary;

    private Integer score;

    /** Full structured analysis (rubric scores, action items) stored as JSON text. */
    @Column(columnDefinition = "text")
    private String analysisJson;

    private String failureReason;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant analyzedAt;

    protected Call() { }

    public Call(String agentName, String transcript) {
        this.id = UUID.randomUUID();
        this.agentName = agentName;
        this.transcript = transcript;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void recordAttempt() { attempts++; }

    public void markAnalyzed(String summary, int score, String analysisJson) {
        this.status = Status.ANALYZED;
        this.summary = summary;
        this.score = score;
        this.analysisJson = analysisJson;
        this.failureReason = null;
        this.analyzedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = Status.FAILED;
        this.failureReason = reason.length() > 250 ? reason.substring(0, 250) : reason;
    }

    public UUID getId() { return id; }
    public String getAgentName() { return agentName; }
    public String getTranscript() { return transcript; }
    public Status getStatus() { return status; }
    public String getSummary() { return summary; }
    public Integer getScore() { return score; }
    public String getAnalysisJson() { return analysisJson; }
    public String getFailureReason() { return failureReason; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAnalyzedAt() { return analyzedAt; }
}
