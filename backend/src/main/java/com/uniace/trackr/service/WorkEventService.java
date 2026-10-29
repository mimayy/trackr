package com.uniace.trackr.service;

import com.uniace.trackr.domain.*;
import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.dto.WebAuthnDtos.VerificationResult;
import com.uniace.trackr.dto.WorkEventDtos.RecordEventRequest;
import com.uniace.trackr.repo.*;
import com.uniace.trackr.service.LocationService.LocationMatch;
import com.uniace.trackr.service.workflow.WorkflowEngine;
import com.uniace.trackr.web.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records work events: the single path every Time In, arrival and departure
 * takes, whether it arrives live from the phone or later from the offline
 * queue.
 *
 * <p>Two ideas run through it:
 *
 * <ul>
 *   <li><b>Blocked versus flagged.</b> An event that cannot be safely accepted
 *       is refused with an explanation the employee can act on. An event that
 *       merely raises questions is stored and marked for review. Questionable
 *       records are never silently dropped.</li>
 *   <li><b>Offline is more forgiving.</b> The same checks run on a synced
 *       event, but problems that would be refused live are recorded as flags
 *       instead - the employee has already done the work, and losing the record
 *       would be worse than reviewing it.</li>
 * </ul>
 */
@Service
public class WorkEventService {

    private static final Logger log = LoggerFactory.getLogger(WorkEventService.class);

    private final WorkEventRepository workEventRepository;
    private final PhotoEvidenceRepository photoRepository;
    private final WorkLocationRepository locationRepository;
    private final LocationService locationService;
    private final WebAuthnService webAuthnService;
    private final SettingsService settingsService;

    public WorkEventService(WorkEventRepository workEventRepository,
                            PhotoEvidenceRepository photoRepository,
                            WorkLocationRepository locationRepository,
                            LocationService locationService,
                            WebAuthnService webAuthnService,
                            SettingsService settingsService) {
        this.workEventRepository = workEventRepository;
        this.photoRepository = photoRepository;
        this.locationRepository = locationRepository;
        this.locationService = locationService;
        this.webAuthnService = webAuthnService;
        this.settingsService = settingsService;
    }

    /**
     * The outcome of recording one event.
     *
     * <p>The flags are returned alongside the event rather than being read back
     * from it, because the caller may well be outside the transaction that
     * stored it - and a lazily loaded collection would fail there.
     */
    public record RecordResult(WorkEvent event, boolean duplicate, List<WorkEventFlag> flags) {

        public boolean flagged() {
            return flags.stream()
                    .anyMatch(f -> f.getSeverity() == FlagSeverity.WARNING || f.getSeverity() == FlagSeverity.CRITICAL);
        }
    }

    /**
     * Refusal of an event that cannot be accepted. The message is written for
     * the employee; the code lets the front end react (for example by offering
     * "TRY AGAIN" after a geofence miss).
     */
    public static class BlockedEventException extends RuntimeException {
        private final String code;

        public BlockedEventException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    // -------------------------------------------------------------------------
    // Recording
    // -------------------------------------------------------------------------

    /** Records an event captured while online. Problems are refused, not stored. */
    @Transactional
    public RecordResult recordOnline(UserAccount user, Employee employee, RecordEventRequest request) {
        try {
            return record(user, employee, request, false);
        } catch (BlockedEventException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage());
        }
    }

    /**
     * Records an event that was queued offline. Problems become flags.
     *
     * <p>This is the transaction boundary for one queued event, and it is
     * {@code REQUIRES_NEW} on purpose: an event the server refuses outright must
     * roll back on its own, without dragging down the rest of the batch or
     * leaving a doomed transaction for the caller to commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecordResult recordFromSync(UserAccount user, Employee employee, RecordEventRequest request) {
        return record(user, employee, request, true);
    }

    private RecordResult record(UserAccount user, Employee employee, RecordEventRequest request, boolean lenient) {

        // ---- Idempotency ----------------------------------------------------
        // The device generated this id. Seeing it twice means a retry, not a
        // second Time In, so the stored event is returned unchanged.
        Optional<WorkEvent> existing = workEventRepository.findById(request.eventId());
        if (existing.isPresent()) {
            return new RecordResult(existing.get(), true, List.of());
        }

        // ---- Hard preconditions --------------------------------------------
        if (employee.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BlockedEventException("ACCOUNT_NOT_ACTIVE",
                    "Your account is not active. Please contact your administrator.");
        }

        Instant now = Instant.now();
        Instant eventTime = request.eventTime();
        if (eventTime.isAfter(now.plus(Duration.ofDays(1)))) {
            throw new BlockedEventException("EVENT_TIME_INVALID",
                    "The time on this activity does not look right. Please check your phone's date and time.");
        }

        ZoneId zone = scheduleZone(employee);
        LocalDate businessDate = eventTime.atZone(zone).toLocalDate();

        List<WorkEvent> eventsToday = workEventRepository
                .findByEmployeeIdAndBusinessDateOrderByEventTimeAsc(employee.getId(), businessDate);

        WorkEvent event = new WorkEvent();
        List<WorkEventFlag> flags = new ArrayList<>();

        event.setId(request.eventId());
        event.setEmployee(employee);
        event.setEventType(request.eventType());
        event.setEventTime(eventTime);
        event.setDeviceTimestamp(request.deviceTimestamp());
        event.setBusinessDate(businessDate);
        event.setCaptureMode(request.captureModeOrOnline());
        event.setSyncTimestamp(request.captureModeOrOnline() == CaptureMode.OFFLINE
                ? (request.syncTimestamp() == null ? now : request.syncTimestamp())
                : now);
        event.setReceivedAt(now);
        event.setSyncStatus(SyncStatus.SYNCED);
        event.setIntegrityHash(request.integrityHash());
        event.setClientAppVersion(request.clientAppVersion());

        // ---- Sequence -------------------------------------------------------
        validateSequence(employee, request, eventsToday, businessDate, lenient, flags);

        // ---- Position -------------------------------------------------------
        applyLocation(event, request, lenient, flags);

        // ---- Which site ------------------------------------------------------
        applySiteName(event, request, lenient, flags);

        // ---- Destination ----------------------------------------------------
        applyDestination(event, request, lenient, flags);

        // ---- Fingerprint ----------------------------------------------------
        applyBiometric(event, user, employee, request, lenient, flags);

        // ---- Offline integrity ---------------------------------------------
        applyOfflineChecks(event, request, now, flags);

        // ---- Duration sanity ------------------------------------------------
        applyDurationChecks(event, eventsToday, flags);

        // ---- Photo ----------------------------------------------------------
        applyPhoto(event, employee, request);

        // ---- Disposition ----------------------------------------------------
        for (WorkEventFlag flag : flags) {
            event.addFlag(flag);
        }
        boolean needsReview = flags.stream()
                .anyMatch(f -> f.getSeverity() == FlagSeverity.WARNING || f.getSeverity() == FlagSeverity.CRITICAL);
        if (needsReview) {
            event.setDisposition(Disposition.FLAGGED);
            event.setReviewStatus(ReviewStatus.REQUIRES_REVIEW);
            event.setSyncStatus(SyncStatus.FLAGGED);
        }

        WorkEvent saved = workEventRepository.save(event);
        return new RecordResult(saved, false, List.copyOf(flags));
    }

    // -------------------------------------------------------------------------
    // Individual checks
    // -------------------------------------------------------------------------

    private void validateSequence(Employee employee,
                                  RecordEventRequest request,
                                  List<WorkEvent> eventsToday,
                                  LocalDate businessDate,
                                  boolean lenient,
                                  List<WorkEventFlag> flags) {

        WorkflowState state = WorkflowEngine.stateFor(eventsToday);
        Optional<String> problem = WorkflowEngine.validate(state, request.eventType());
        if (problem.isEmpty()) {
            return;
        }

        boolean duplicateTimeIn = request.eventType() == EventType.TIME_IN
                && state != WorkflowState.NOT_STARTED;

        String code = duplicateTimeIn ? "DUPLICATE_TIME_IN" : "INVALID_SEQUENCE";

        if (!lenient) {
            throw new BlockedEventException(code, problem.get());
        }

        // Synced from the queue: keep the record and mark it for review rather
        // than discarding work the employee actually did.
        flags.add(new WorkEventFlag(FlagCategory.WORKFLOW, code, FlagSeverity.WARNING, problem.get()));
    }

    private void applyLocation(WorkEvent event,
                               RecordEventRequest request,
                               boolean lenient,
                               List<WorkEventFlag> flags) {

        if (request.latitude() == null || request.longitude() == null) {
            if (!lenient) {
                throw new BlockedEventException("GPS_UNAVAILABLE",
                        "We can't get your location. Please turn on Location Services and try again.");
            }
            flags.add(new WorkEventFlag(FlagCategory.LOCATION, "GPS_UNAVAILABLE",
                    FlagSeverity.WARNING, "Recorded without a position."));
            return;
        }

        event.setLatitude(request.latitude());
        event.setLongitude(request.longitude());
        event.setGpsAccuracyM(request.gpsAccuracyM());

        double maxAccuracy = settingsService.gpsMaxAccuracyMeters();
        double warnAccuracy = settingsService.gpsWarnAccuracyMeters();
        Double accuracy = request.gpsAccuracyM();

        if (accuracy != null && accuracy > maxAccuracy) {
            if (!lenient) {
                throw new BlockedEventException("GPS_ACCURACY_TOO_LOW",
                        "We can't accurately verify your location. Please move to an open area and try again.");
            }
            flags.add(new WorkEventFlag(FlagCategory.LOCATION, "GPS_ACCURACY_TOO_LOW", FlagSeverity.WARNING,
                    "Reported accuracy was " + Math.round(accuracy) + " m."));
        } else if (accuracy != null && accuracy > warnAccuracy) {
            flags.add(new WorkEventFlag(FlagCategory.LOCATION, "GPS_ACCURACY_LOW", FlagSeverity.INFO,
                    "Reported accuracy was " + Math.round(accuracy) + " m."));
        }

        // Nearest known site, for naming only. Nothing is refused on the
        // strength of it: Uni-Ace's warehouses are not a fixed list, so being
        // far from every configured location is ordinary rather than suspect.
        LocationMatch match = locationService.resolve(request.latitude(), request.longitude(), accuracy);
        if (!match.matched()) {
            return;
        }

        event.setLocation(match.location());
        event.setDistanceToLocationM(match.distanceMeters());
        event.setWithinGeofence(match.withinGeofence());
    }

    /**
     * Records which warehouse or site the employee says they are at.
     *
     * <p>Typed rather than chosen from a list. The warehouses change, and an
     * employee standing at one nobody has added yet must still be able to
     * record their arrival - so this is the one thing Field Arrival insists on,
     * and it is never checked against coordinates.
     */
    private void applySiteName(WorkEvent event,
                               RecordEventRequest request,
                               boolean lenient,
                               List<WorkEventFlag> flags) {

        if (event.getEventType() != EventType.FIELD_ARRIVAL) {
            return;
        }

        String name = request.siteName() == null ? "" : request.siteName().trim();

        if (name.isEmpty()) {
            if (!lenient) {
                throw new BlockedEventException("MISSING_SITE_NAME",
                        "Please enter the name of the warehouse or site you are at.");
            }
            flags.add(new WorkEventFlag(FlagCategory.WORKFLOW, "MISSING_SITE_NAME",
                    FlagSeverity.WARNING, "Arrival recorded without a site name."));
            return;
        }

        // Trimmed to the column width rather than refused: losing the tail of a
        // long name is better than losing the arrival.
        event.setSiteName(name.length() > 120 ? name.substring(0, 120) : name);
    }

    private void applyDestination(WorkEvent event,
                                  RecordEventRequest request,
                                  boolean lenient,
                                  List<WorkEventFlag> flags) {

        if (request.eventType() != EventType.FIELD_DEPARTURE) {
            return;
        }

        if (request.destination() == null) {
            if (!lenient) {
                throw new BlockedEventException("MISSING_DESTINATION",
                        "Please choose where you are going next.");
            }
            flags.add(new WorkEventFlag(FlagCategory.WORKFLOW, "MISSING_DESTINATION",
                    FlagSeverity.WARNING, "No destination was chosen."));
            return;
        }

        event.setDestination(request.destination());

        if (request.destination() == Destination.ANOTHER_FIELD_LOCATION) {
            if (request.destinationLocationId() == null) {
                if (!lenient) {
                    throw new BlockedEventException("MISSING_DESTINATION",
                            "Please choose which location you are going to.");
                }
                flags.add(new WorkEventFlag(FlagCategory.WORKFLOW, "MISSING_DESTINATION",
                        FlagSeverity.WARNING, "Another field location was chosen without saying which."));
                return;
            }
            WorkLocation destination = locationRepository.findById(request.destinationLocationId())
                    .orElseThrow(() -> new BlockedEventException("DESTINATION_NOT_FOUND",
                            "That location could not be found. Please choose it again."));
            event.setDestinationLocation(destination);
        }
    }

    /**
     * Every activity recorded online must carry a fingerprint the server can
     * verify. Offline the server cannot issue a challenge, so a queued activity
     * is kept without one and sent to a supervisor for review instead.
     */
    private void applyBiometric(WorkEvent event,
                                UserAccount user,
                                Employee employee,
                                RecordEventRequest request,
                                boolean lenient,
                                List<WorkEventFlag> flags) {

        event.setBiometricVerified(false);

        if (request.deviceAssertion() == null || !request.deviceAssertion().isComplete()) {
            if (!lenient) {
                if (webAuthnService.activeCredential(employee).isEmpty()) {
                    throw new BlockedEventException("FINGERPRINT_NOT_SET_UP",
                            "Please set up your fingerprint in Profile before recording an activity.");
                }
                throw new BlockedEventException("FINGERPRINT_REQUIRED",
                        "Please confirm with your fingerprint to record this activity.");
            }
            flags.add(new WorkEventFlag(FlagCategory.AUTHENTICATION, "NO_FINGERPRINT", FlagSeverity.WARNING,
                    "Recorded without a fingerprint check."));
            return;
        }

        VerificationResult result = webAuthnService.verifyAssertion(user, employee, request.deviceAssertion());
        if (result.verified()) {
            event.setBiometricVerified(true);
            webAuthnService.activeCredential(employee).ifPresent(event::setCredential);
            if (result.signCountAnomaly()) {
                flags.add(new WorkEventFlag(FlagCategory.AUTHENTICATION, "SIGN_COUNT_ANOMALY", FlagSeverity.WARNING,
                        "The device's signature counter did not advance as expected."));
            }
            return;
        }

        String reason = result.failureReason() == null ? "The fingerprint check did not verify." : result.failureReason();
        boolean revoked = reason.toLowerCase().contains("revoked");
        String code = revoked ? "CREDENTIAL_REVOKED" : "FINGERPRINT_FAILED";
        if (!lenient) {
            throw new BlockedEventException(code, revoked
                    ? "This phone's fingerprint has been revoked. Please contact your administrator."
                    : "Your fingerprint could not be verified. Please try again.");
        }
        flags.add(new WorkEventFlag(FlagCategory.AUTHENTICATION, code, FlagSeverity.WARNING, reason));
    }

    private void applyOfflineChecks(WorkEvent event,
                                    RecordEventRequest request,
                                    Instant now,
                                    List<WorkEventFlag> flags) {

        if (event.getCaptureMode() != CaptureMode.OFFLINE) {
            return;
        }

        long skewLimit = settingsService.maxClockSkewMinutes();

        // The phone's own clock at capture should agree with the event time it
        // claims. A large gap does not prove anything on its own - it is
        // recorded so a human can judge it.
        if (request.deviceTimestamp() != null) {
            long skew = Math.abs(Duration.between(request.deviceTimestamp(), event.getEventTime()).toMinutes());
            if (skew > skewLimit) {
                flags.add(new WorkEventFlag(FlagCategory.OFFLINE_SYNC, "CLOCK_SKEW", FlagSeverity.WARNING,
                        "The device clock differed from the recorded time by " + skew + " minutes."));
            }
        }

        // An event dated in the future relative to the server cannot be right.
        long aheadMinutes = Duration.between(now, event.getEventTime()).toMinutes();
        if (aheadMinutes > skewLimit) {
            flags.add(new WorkEventFlag(FlagCategory.OFFLINE_SYNC, "CLOCK_SKEW", FlagSeverity.WARNING,
                    "Recorded " + aheadMinutes + " minutes ahead of server time."));
        }

        long queuedHours = Duration.between(event.getEventTime(), now).toHours();
        if (queuedHours >= 24) {
            flags.add(new WorkEventFlag(FlagCategory.OFFLINE_SYNC, "LATE_SYNC", FlagSeverity.INFO,
                    "Uploaded " + queuedHours + " hours after it was recorded."));
        }
    }

    private void applyDurationChecks(WorkEvent event, List<WorkEvent> eventsToday, List<WorkEventFlag> flags) {
        if (event.getEventType() != EventType.TIME_OUT) {
            return;
        }

        Optional<Instant> timeIn = eventsToday.stream()
                .filter(e -> e.getEventType() == EventType.TIME_IN)
                .map(WorkEvent::getEventTime)
                .min(Instant::compareTo);

        if (timeIn.isEmpty()) {
            return;
        }

        Duration elapsed = Duration.between(timeIn.get(), event.getEventTime());
        if (elapsed.isNegative()) {
            flags.add(new WorkEventFlag(FlagCategory.ATTENDANCE, "TIME_OUT_BEFORE_TIME_IN", FlagSeverity.CRITICAL,
                    "Time Out is earlier than Time In."));
            return;
        }

        int maxHours = settingsService.attendanceRules().maxWorkdayHours();
        if (elapsed.toHours() > maxHours) {
            flags.add(new WorkEventFlag(FlagCategory.ATTENDANCE, "IMPOSSIBLE_DURATION", FlagSeverity.WARNING,
                    "The workday would be " + elapsed.toHours() + " hours long."));
        }
    }

    private void applyPhoto(WorkEvent event, Employee employee, RecordEventRequest request) {
        if (request.photoId() == null) {
            return;
        }

        PhotoEvidence photo = photoRepository.findById(request.photoId())
                .orElseThrow(() -> new BlockedEventException("PHOTO_NOT_FOUND",
                        "That photo is no longer available. You can record this activity without it."));

        if (!photo.getEmployee().getId().equals(employee.getId())) {
            throw new BlockedEventException("PHOTO_NOT_YOURS", "That photo belongs to another employee.");
        }
        if (photo.getStatus() == PhotoStatus.ATTACHED) {
            throw new BlockedEventException("PHOTO_ALREADY_USED", "That photo is already attached to another activity.");
        }

        photo.setStatus(PhotoStatus.ATTACHED);
        photoRepository.save(photo);
        event.setPhoto(photo);
    }

    /** The timezone the employee's business date is calculated in. */
    /** Warehouse names this employee has used before, for the arrival screen. */
    @Transactional(readOnly = true)
    public List<String> recentSiteNames(Employee employee) {
        return workEventRepository.findRecentSiteNames(
                employee.getId(), org.springframework.data.domain.PageRequest.of(0, 8));
    }

    public ZoneId scheduleZone(Employee employee) {
        try {
            if (employee.getWorkSchedule() != null && employee.getWorkSchedule().getTimezone() != null) {
                return ZoneId.of(employee.getWorkSchedule().getTimezone());
            }
        } catch (RuntimeException ex) {
            log.warn("Employee {} has an unusable schedule timezone; falling back to Asia/Manila",
                    employee.getEmployeeNo(), ex);
        }
        return ZoneId.of("Asia/Manila");
    }

    @Transactional(readOnly = true)
    public List<WorkEvent> eventsForDay(Employee employee, LocalDate date) {
        return workEventRepository.findByEmployeeIdAndBusinessDateOrderByEventTimeAsc(employee.getId(), date);
    }

    @Transactional(readOnly = true)
    public List<WorkEvent> eventsBetween(Employee employee, LocalDate from, LocalDate to) {
        return workEventRepository.findByEmployeeIdAndBusinessDateBetweenOrderByEventTimeAsc(
                employee.getId(), from, to);
    }

    @Transactional(readOnly = true)
    public WorkEvent require(java.util.UUID id) {
        return workEventRepository.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("That activity could not be found."));
    }
}
