package com.uniace.trackr.service;

import com.uniace.trackr.domain.EventAdjustment;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.WorkEvent;
import com.uniace.trackr.domain.WorkEventFlag;
import com.uniace.trackr.domain.enums.AdjustmentField;
import com.uniace.trackr.domain.enums.AdjustmentStatus;
import com.uniace.trackr.domain.enums.FlagCategory;
import com.uniace.trackr.domain.enums.FlagSeverity;
import com.uniace.trackr.domain.enums.ReviewStatus;
import com.uniace.trackr.dto.AdminDtos.AdjustmentRequest;
import com.uniace.trackr.dto.AdminDtos.AdjustmentRow;
import com.uniace.trackr.dto.AdminDtos.ExceptionRow;
import com.uniace.trackr.dto.AdminDtos.ReviewRequest;
import com.uniace.trackr.repo.EventAdjustmentRepository;
import com.uniace.trackr.repo.UserAccountRepository;
import com.uniace.trackr.repo.WorkEventRepository;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.web.error.ApiException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The exceptions queue and corrections.
 *
 * <p>Two rules shape this: nothing questionable is deleted, and nothing
 * accepted is overwritten. A record that raised a flag stays exactly as it was
 * captured; a correction is a separate, reasoned, approved entry layered on
 * top, so the original and the fix are both visible forever.
 */
@Service
public class ReviewService {

    private final WorkEventRepository workEventRepository;
    private final EventAdjustmentRepository adjustmentRepository;
    private final UserAccountRepository userRepository;
    private final ScopeService scopeService;
    private final AuditService auditService;
    private final EventMapper mapper;

    public ReviewService(WorkEventRepository workEventRepository,
                         EventAdjustmentRepository adjustmentRepository,
                         UserAccountRepository userRepository,
                         ScopeService scopeService,
                         AuditService auditService,
                         EventMapper mapper) {
        this.workEventRepository = workEventRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.userRepository = userRepository;
        this.scopeService = scopeService;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    /** Everything currently waiting for a human look, newest first. */
    @Transactional(readOnly = true)
    public List<ExceptionRow> queue(AuthPrincipal principal, FlagCategory category) {
        List<UUID> visible = scopeService.visibleEmployeeIds(principal);

        return workEventRepository.findByReviewStatus(ReviewStatus.REQUIRES_REVIEW).stream()
                .filter(event -> principal.isAdmin() || visible.contains(event.getEmployee().getId()))
                .filter(event -> category == null
                        || event.getFlags().stream().anyMatch(flag -> flag.getCategory() == category))
                .map(this::toExceptionRow)
                .sorted(Comparator.comparing(ExceptionRow::eventTime).reversed())
                .toList();
    }

    @Transactional
    public ExceptionRow review(AuthPrincipal principal, UUID eventId, ReviewRequest request) {
        WorkEvent event = workEventRepository.findDetailedById(eventId)
                .orElseThrow(() -> ApiException.notFound("That activity could not be found."));
        scopeService.requireVisibleEmployee(principal, event.getEmployee().getId());

        if (request.reviewStatus() == ReviewStatus.NONE
                || request.reviewStatus() == ReviewStatus.REQUIRES_REVIEW) {
            throw ApiException.badRequest("REVIEW_STATUS_INVALID",
                    "Please mark the activity as reviewed, or as reviewed with a correction.");
        }

        UserAccount reviewer = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Please log in again."));

        event.setReviewStatus(request.reviewStatus());
        event.setReviewedBy(reviewer);
        event.setReviewedAt(Instant.now());
        event.setReviewNotes(request.notes());
        WorkEvent saved = workEventRepository.save(event);

        auditService.success(principal, "REVIEW_WORK_EVENT", "WorkEvent", saved.getId(),
                saved.getEmployee().fullName(),
                request.reviewStatus() + (request.notes() == null ? "" : " - " + request.notes()));

        return toExceptionRow(saved);
    }

    // -------------------------------------------------------------------------
    // Corrections
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdjustmentRow> adjustmentsFor(AuthPrincipal principal, UUID eventId) {
        WorkEvent event = workEventRepository.findDetailedById(eventId)
                .orElseThrow(() -> ApiException.notFound("That activity could not be found."));
        scopeService.requireVisibleEmployee(principal, event.getEmployee().getId());

        return adjustmentRepository.findByWorkEventIdOrderByRequestedAtAsc(eventId).stream()
                .map(ReviewService::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdjustmentRow> pendingAdjustments() {
        return adjustmentRepository.findByStatusOrderByRequestedAtDesc(AdjustmentStatus.PENDING).stream()
                .map(ReviewService::toRow)
                .toList();
    }

    /**
     * Records a correction request against an event. The event itself is not
     * touched: the request captures what it said, what it should say, why, and
     * who asked.
     */
    @Transactional
    public AdjustmentRow requestAdjustment(AuthPrincipal principal, UUID eventId, AdjustmentRequest request) {
        WorkEvent event = workEventRepository.findDetailedById(eventId)
                .orElseThrow(() -> ApiException.notFound("That activity could not be found."));
        scopeService.requireVisibleEmployee(principal, event.getEmployee().getId());

        UserAccount requester = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Please log in again."));

        EventAdjustment adjustment = new EventAdjustment();
        adjustment.setWorkEvent(event);
        adjustment.setField(request.field());
        adjustment.setOriginalValue(currentValue(event, request.field()));
        adjustment.setAdjustedValue(request.adjustedValue());
        adjustment.setReason(request.reason());
        adjustment.setStatus(AdjustmentStatus.PENDING);
        adjustment.setRequestedBy(requester);

        EventAdjustment saved = adjustmentRepository.save(adjustment);

        auditService.success(principal, "REQUEST_EVENT_ADJUSTMENT", "WorkEvent", event.getId(),
                event.getEmployee().fullName(),
                request.field() + ": " + saved.getOriginalValue() + " -> " + saved.getAdjustedValue()
                        + " (" + request.reason() + ")");

        return toRow(saved);
    }

    /**
     * Approves or rejects a correction. Approval marks the event as reviewed
     * with an adjustment; the original values stay in place and the adjustment
     * record carries the corrected reading.
     */
    @Transactional
    public AdjustmentRow decide(AuthPrincipal principal, UUID adjustmentId, AdjustmentStatus decision, String note) {
        if (decision != AdjustmentStatus.APPROVED && decision != AdjustmentStatus.REJECTED) {
            throw ApiException.badRequest("DECISION_INVALID", "Please approve or reject the correction.");
        }

        EventAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> ApiException.notFound("That correction could not be found."));

        if (adjustment.getStatus() != AdjustmentStatus.PENDING) {
            throw ApiException.badRequest("DECISION_ALREADY_MADE", "This correction has already been decided.");
        }

        UserAccount decider = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Please log in again."));

        adjustment.setStatus(decision);
        adjustment.setDecidedBy(decider);
        adjustment.setDecidedAt(Instant.now());
        adjustment.setDecisionNote(note);
        EventAdjustment saved = adjustmentRepository.save(adjustment);

        if (decision == AdjustmentStatus.APPROVED) {
            WorkEvent event = adjustment.getWorkEvent();
            event.setReviewStatus(ReviewStatus.REVIEWED_ADJUSTED);
            event.setReviewedBy(decider);
            event.setReviewedAt(Instant.now());
            workEventRepository.save(event);
        }

        auditService.success(principal, "DECIDE_EVENT_ADJUSTMENT", "EventAdjustment", saved.getId(),
                adjustment.getWorkEvent().getEmployee().fullName(),
                decision + (note == null ? "" : " - " + note));

        return toRow(saved);
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private ExceptionRow toExceptionRow(WorkEvent event) {
        FlagSeverity highest = event.getFlags().stream()
                .map(WorkEventFlag::getSeverity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(FlagSeverity.INFO);

        return new ExceptionRow(
                event.getId(),
                event.getEmployee().getId(),
                event.getEmployee().fullName(),
                event.getEmployee().getEmployeeNo(),
                event.getEventType(),
                Labels.eventLabel(event.getEventType()),
                event.getEventTime(),
                event.getBusinessDate(),
                event.getLocation() == null ? null : event.getLocation().getName(),
                event.getCaptureMode(),
                event.getReviewStatus(),
                mapper.toFlagViews(event.getFlags()),
                highest);
    }

    private static AdjustmentRow toRow(EventAdjustment adjustment) {
        return new AdjustmentRow(
                adjustment.getId(),
                adjustment.getWorkEvent().getId(),
                adjustment.getField(),
                adjustment.getOriginalValue(),
                adjustment.getAdjustedValue(),
                adjustment.getReason(),
                adjustment.getStatus(),
                adjustment.getRequestedBy() == null ? null : adjustment.getRequestedBy().getDisplayName(),
                adjustment.getRequestedAt(),
                adjustment.getDecidedBy() == null ? null : adjustment.getDecidedBy().getDisplayName(),
                adjustment.getDecidedAt(),
                adjustment.getDecisionNote());
    }

    private static String currentValue(WorkEvent event, AdjustmentField field) {
        return switch (field) {
            case EVENT_TIME -> event.getEventTime().toString();
            case LOCATION -> event.getLocation() == null ? "(none)" : event.getLocation().getName();
            case EVENT_TYPE -> event.getEventType().name();
            case SITE_NAME -> event.getSiteName() == null ? "(none)" : event.getSiteName();
            case REVIEW_NOTE -> event.getReviewNotes() == null ? "(none)" : event.getReviewNotes();
        };
    }
}
