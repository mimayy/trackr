package com.uniace.trackr.domain;

import com.uniace.trackr.domain.enums.FlagCategory;
import com.uniace.trackr.domain.enums.FlagSeverity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A reason a work event needs a human look. Flags never accuse anyone; they
 * describe what the system could not verify.
 */
@Entity
@Table(name = "work_event_flags")
public class WorkEventFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_event_id", nullable = false)
    private WorkEvent workEvent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FlagCategory category;

    @Column(nullable = false, length = 60)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlagSeverity severity;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WorkEventFlag() {
    }

    public WorkEventFlag(FlagCategory category, String code, FlagSeverity severity, String detail) {
        this.category = category;
        this.code = code;
        this.severity = severity;
        this.detail = detail;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public WorkEvent getWorkEvent() { return workEvent; }
    public void setWorkEvent(WorkEvent workEvent) { this.workEvent = workEvent; }

    public FlagCategory getCategory() { return category; }
    public void setCategory(FlagCategory category) { this.category = category; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public FlagSeverity getSeverity() { return severity; }
    public void setSeverity(FlagSeverity severity) { this.severity = severity; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
