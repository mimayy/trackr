package com.uniace.trackr.domain;

import com.uniace.trackr.domain.enums.AdjustmentField;
import com.uniace.trackr.domain.enums.AdjustmentStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A correction applied on top of a work event.
 *
 * <p>An accepted work event is never edited in place. The original stays
 * exactly as recorded and the correction is layered over it, with a reason and
 * an approver (requirement 60).
 */
@Entity
@Table(name = "event_adjustments")
public class EventAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_event_id", nullable = false)
    private WorkEvent workEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_name", nullable = false, length = 60)
    private AdjustmentField field;

    @Column(name = "original_value", length = 500)
    private String originalValue;

    @Column(name = "adjusted_value", length = 500)
    private String adjustedValue;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdjustmentStatus status = AdjustmentStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private UserAccount requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private UserAccount decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public WorkEvent getWorkEvent() { return workEvent; }
    public void setWorkEvent(WorkEvent workEvent) { this.workEvent = workEvent; }

    public AdjustmentField getField() { return field; }
    public void setField(AdjustmentField field) { this.field = field; }

    public String getOriginalValue() { return originalValue; }
    public void setOriginalValue(String originalValue) { this.originalValue = originalValue; }

    public String getAdjustedValue() { return adjustedValue; }
    public void setAdjustedValue(String adjustedValue) { this.adjustedValue = adjustedValue; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public AdjustmentStatus getStatus() { return status; }
    public void setStatus(AdjustmentStatus status) { this.status = status; }

    public UserAccount getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UserAccount requestedBy) { this.requestedBy = requestedBy; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public UserAccount getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UserAccount decidedBy) { this.decidedBy = decidedBy; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
}
