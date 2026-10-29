package com.uniace.trackr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniace.trackr.domain.DeviceCredential;
import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.WorkLocation;
import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.dto.WebAuthnDtos.DeviceAssertion;
import com.uniace.trackr.dto.WebAuthnDtos.VerificationResult;
import com.uniace.trackr.dto.WorkEventDtos.FlagView;
import com.uniace.trackr.dto.WorkEventDtos.RecordEventRequest;
import com.uniace.trackr.dto.WorkEventDtos.SyncResponse;
import com.uniace.trackr.repo.WorkEventRepository;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.support.DatabaseCleaner;
import com.uniace.trackr.support.TestFixtures;
import com.uniace.trackr.web.error.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Recording work events, end to end against a real database.
 *
 * Covers the properties that matter most in production: the sequence cannot be
 * bypassed, questionable records are kept rather than dropped, and the offline
 * queue can be retried without creating duplicates.
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkEventServiceIntegrationTest {

    @Autowired private WorkEventService workEventService;
    @Autowired private SyncService syncService;
    @Autowired private WorkEventRepository workEventRepository;
    @Autowired private TestFixtures fixtures;
    @Autowired private DatabaseCleaner cleaner;

    /**
     * A real assertion needs a platform authenticator, so the check itself is
     * stood in for; WebAuthnService has its own coverage. What is tested here
     * is what recording does with the answer.
     */
    @MockBean private WebAuthnService webAuthnService;

    private Employee employee;
    private UserAccount user;
    private WorkLocation office;
    private WorkLocation warehouse;

    /**
     * These tests commit rather than rolling back: offline synchronisation
     * stores each event in its own {@code REQUIRES_NEW} transaction, which a
     * test-managed transaction would hide the fixtures from.
     */
    @AfterEach
    void tearDown() {
        cleaner.clean();
    }

    @BeforeEach
    void setUp() {
        cleaner.clean();
        office = fixtures.manilaOffice();
        warehouse = fixtures.warehouseTwo();
        employee = fixtures.employee("EMP-001", "Juan", "Dela Cruz", "juan@test.example",
                Branch.MANILA, WorkCategory.FIELD_BASED, null, AccountStatus.ACTIVE);
        user = employee.getUser();

        when(webAuthnService.activeCredential(any())).thenReturn(Optional.empty());
        when(webAuthnService.verifyAssertion(any(), any(), any()))
                .thenReturn(VerificationResult.ok("credential", false));
    }

    /* ---------------------------------------------------------------------- */
    /* Online recording                                                       */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("a Time In at the office is accepted and matched to the office")
    void timeInAtOffice() {
        var result = workEventService.recordOnline(user, employee, request(EventType.TIME_IN, office));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.event().getEventType()).isEqualTo(EventType.TIME_IN);
        assertThat(result.event().getLocation().getName()).isEqualTo("Manila Office");
        assertThat(result.event().getWithinGeofence()).isTrue();
        assertThat(result.event().getDisposition()).isEqualTo(Disposition.ACCEPTED);
        assertThat(result.event().getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
    }

    @Test
    @DisplayName("a Time Out before any Time In is refused with an instruction")
    void timeOutWithoutTimeIn() {
        assertThatThrownBy(() -> workEventService.recordOnline(user, employee, request(EventType.TIME_OUT, office)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Time In first");

        assertThat(workEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("a second Time In on the same day is refused")
    void duplicateTimeIn() {
        workEventService.recordOnline(user, employee, request(EventType.TIME_IN, office));

        assertThatThrownBy(() -> workEventService.recordOnline(user, employee, request(EventType.TIME_IN, office)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("an office employee can complete a plain Time In / Time Out day")
    void officeWorkday() {
        workEventService.recordOnline(user, employee, request(EventType.TIME_IN, office));
        var timeOut = workEventService.recordOnline(user, employee, request(EventType.TIME_OUT, office));

        assertThat(timeOut.event().getEventType()).isEqualTo(EventType.TIME_OUT);
        assertThat(workEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("the full office to field and back sequence is accepted in order")
    void officeFieldOfficeWorkday() {
        workEventService.recordOnline(user, employee, request(EventType.TIME_IN, office));
        workEventService.recordOnline(user, employee, request(EventType.OFFICE_DEPARTURE, office));

        var arrival = withSite(request(EventType.FIELD_ARRIVAL, warehouse), "Northgate Cold Storage");
        var recorded = workEventService.recordOnline(user, employee, arrival);
        assertThat(recorded.event().getSiteName()).isEqualTo("Northgate Cold Storage");

        var departure = withDestination(
                request(EventType.FIELD_DEPARTURE, warehouse), Destination.OFFICE);
        workEventService.recordOnline(user, employee, departure);

        workEventService.recordOnline(user, employee, request(EventType.OFFICE_ARRIVAL, office));
        workEventService.recordOnline(user, employee, request(EventType.TIME_OUT, office));

        assertThat(workEventRepository.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("a Field Departure with no destination is refused")
    void departureWithoutDestination() {
        workEventService.recordOnline(user, employee, request(EventType.TIME_IN, office));
        workEventService.recordOnline(user, employee, request(EventType.OFFICE_DEPARTURE, office));
        workEventService.recordOnline(user, employee,
                withSite(request(EventType.FIELD_ARRIVAL, warehouse), "Northgate Cold Storage"));

        assertThatThrownBy(() -> workEventService.recordOnline(user, employee,
                request(EventType.FIELD_DEPARTURE, warehouse)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("where you are going next");
    }

    @Test
    @DisplayName("a field arrival is accepted wherever the employee is standing")
    void arrivalIsNotGeofenced() {
        // Uni-Ace's warehouses are not a fixed list, so being nowhere near a
        // configured location is ordinary. Refusing it would strand an employee
        // at a site nobody had added yet.
        workEventService.recordOnline(user, employee, request(EventType.TIME_IN, office));
        workEventService.recordOnline(user, employee, request(EventType.OFFICE_DEPARTURE, office));

        var somewhereElse = withSite(request(EventType.FIELD_ARRIVAL, office), "Bagong Warehouse");
        var recorded = workEventService.recordOnline(user, employee, somewhereElse);

        assertThat(recorded.event().getSiteName()).isEqualTo("Bagong Warehouse");
        assertThat(recorded.flags())
                .noneMatch(flag -> flag.getCode().equals("OUTSIDE_GEOFENCE"));
    }

    @Test
    @DisplayName("a field arrival with no site name is refused")
    void arrivalNeedsASiteName() {
        workEventService.recordOnline(user, employee, request(EventType.TIME_IN, office));
        workEventService.recordOnline(user, employee, request(EventType.OFFICE_DEPARTURE, office));

        assertThatThrownBy(() -> workEventService.recordOnline(user, employee,
                request(EventType.FIELD_ARRIVAL, warehouse)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("name of the warehouse");
    }

    @Test
    @DisplayName("a position too inaccurate to trust is refused with an instruction")
    void poorGpsAccuracy() {
        RecordEventRequest inaccurate = new RecordEventRequest(
                UUID.randomUUID(), EventType.TIME_IN, Instant.now(), Instant.now(),
                office.getLatitude(), office.getLongitude(), 900.0,
                null, null, null, null, null, CaptureMode.ONLINE, null, null, "test");

        assertThatThrownBy(() -> workEventService.recordOnline(user, employee, inaccurate))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("open area");
    }

    @Test
    @DisplayName("an activity without a fingerprint is refused, and nothing is stored")
    void fingerprintIsRequired() {
        when(webAuthnService.activeCredential(any())).thenReturn(Optional.of(mock(DeviceCredential.class)));

        assertThatThrownBy(() -> workEventService.recordOnline(user, employee,
                withoutFingerprint(request(EventType.TIME_IN, office))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("confirm with your fingerprint");

        assertThat(workEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("an employee with no fingerprint set up is told to set one up")
    void fingerprintMustBeSetUp() {
        assertThatThrownBy(() -> workEventService.recordOnline(user, employee,
                withoutFingerprint(request(EventType.TIME_IN, office))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("set up your fingerprint");
    }

    @Test
    @DisplayName("a fingerprint that does not verify is refused")
    void failedFingerprintIsRefused() {
        when(webAuthnService.verifyAssertion(any(), any(), any()))
                .thenReturn(VerificationResult.failed("Signature did not verify."));

        assertThatThrownBy(() -> workEventService.recordOnline(user, employee, request(EventType.TIME_IN, office)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("could not be verified");

        assertThat(workEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("a verified activity stores the phone's position and the fingerprint result")
    void verifiedActivityKeepsPosition() {
        var recorded = workEventService.recordOnline(user, employee, request(EventType.TIME_IN, warehouse)).event();

        assertThat(recorded.isBiometricVerified()).isTrue();
        assertThat(recorded.getLatitude()).isEqualTo(warehouse.getLatitude());
        assertThat(recorded.getLongitude()).isEqualTo(warehouse.getLongitude());
        assertThat(recorded.getGpsAccuracyM()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("a disabled employee cannot record anything")
    void disabledEmployee() {
        employee.setAccountStatus(AccountStatus.DISABLED);

        assertThatThrownBy(() -> workEventService.recordOnline(user, employee, request(EventType.TIME_IN, office)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
    }

    /* ---------------------------------------------------------------------- */
    /* Offline synchronisation                                                */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("re-sending the same event never creates a second record")
    void syncIsIdempotent() {
        AuthPrincipal principal = principal();
        RecordEventRequest timeIn = offline(request(EventType.TIME_IN, office));

        SyncResponse first = syncService.sync(principal, user, employee, List.of(timeIn));
        SyncResponse second = syncService.sync(principal, user, employee, List.of(timeIn));

        assertThat(first.accepted() + first.flagged()).isEqualTo(1);
        assertThat(second.duplicates()).isEqualTo(1);
        assertThat(second.accepted()).isZero();
        assertThat(workEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a batch uploaded out of order is stored in the order it happened")
    void syncOrdersByEventTime() {
        AuthPrincipal principal = principal();
        Instant start = Instant.now().minusSeconds(7200);

        RecordEventRequest timeIn = at(offline(request(EventType.TIME_IN, office)), start);
        RecordEventRequest timeOut = at(offline(request(EventType.TIME_OUT, office)), start.plusSeconds(3600));

        // Deliberately the wrong way round, as a phone might upload them.
        SyncResponse response = syncService.sync(principal, user, employee, List.of(timeOut, timeIn));

        assertThat(response.failed()).isZero();
        assertThat(workEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("an offline event that would be refused online is kept and flagged instead")
    void offlineProblemsAreFlaggedNotDiscarded() {
        AuthPrincipal principal = principal();

        // No Time In first: online this is refused outright.
        RecordEventRequest orphanDeparture = offline(request(EventType.OFFICE_DEPARTURE, office));
        SyncResponse response = syncService.sync(principal, user, employee, List.of(orphanDeparture));

        assertThat(response.flagged()).isEqualTo(1);
        assertThat(response.failed()).isZero();

        // The reason travels back to the device with the result, so the employee
        // is told their activity was kept and will be reviewed.
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).status()).isEqualTo("FLAGGED");
        assertThat(response.results().get(0).flags())
                .extracting(FlagView::code)
                .contains("INVALID_SEQUENCE");

        // And it really is stored, not discarded.
        var stored = workEventRepository.findAll().get(0);
        assertThat(stored.getDisposition()).isEqualTo(Disposition.FLAGGED);
        assertThat(stored.getReviewStatus()).isEqualTo(ReviewStatus.REQUIRES_REVIEW);
        assertThat(stored.getSyncStatus()).isEqualTo(SyncStatus.FLAGGED);
    }

    @Test
    @DisplayName("a device clock well out of step with the server is flagged for review")
    void clockSkewIsFlagged() {
        AuthPrincipal principal = principal();
        Instant eventTime = Instant.now().minusSeconds(600);

        RecordEventRequest skewed = new RecordEventRequest(
                UUID.randomUUID(), EventType.TIME_IN, eventTime,
                // The phone's own clock claims two hours later than the event it recorded.
                eventTime.plusSeconds(7200),
                office.getLatitude(), office.getLongitude(), 12.0,
                null, null, null, null, null, CaptureMode.OFFLINE, Instant.now(), null, "test");

        SyncResponse response = syncService.sync(principal, user, employee, List.of(skewed));

        assertThat(response.results().get(0).flags())
                .extracting(FlagView::code)
                .contains("CLOCK_SKEW");

        var stored = workEventRepository.findAll().get(0);
        assertThat(stored.getReviewStatus()).isEqualTo(ReviewStatus.REQUIRES_REVIEW);
    }

    @Test
    @DisplayName("one bad event in a batch does not stop the good ones being stored")
    void badEventDoesNotPoisonTheBatch() {
        AuthPrincipal principal = principal();
        Instant start = Instant.now().minusSeconds(7200);

        RecordEventRequest good = at(offline(request(EventType.TIME_IN, office)), start);
        // An impossible time: the server refuses this one outright.
        RecordEventRequest bad = at(offline(request(EventType.TIME_OUT, office)),
                Instant.now().plusSeconds(86_400L * 3));

        SyncResponse response = syncService.sync(principal, user, employee, List.of(good, bad));

        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.accepted() + response.flagged()).isEqualTo(1);
        assertThat(workEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("an offline activity without a fingerprint is kept, with its position, for review")
    void offlineWithoutFingerprintIsFlagged() {
        RecordEventRequest timeIn = withoutFingerprint(offline(request(EventType.TIME_IN, office)));

        SyncResponse response = syncService.sync(principal(), user, employee, List.of(timeIn));

        assertThat(response.flagged()).isEqualTo(1);
        assertThat(response.results().get(0).flags())
                .extracting(FlagView::code)
                .contains("NO_FINGERPRINT");

        var stored = workEventRepository.findAll().get(0);
        assertThat(stored.isBiometricVerified()).isFalse();
        assertThat(stored.getLatitude()).isEqualTo(office.getLatitude());
        assertThat(stored.getReviewStatus()).isEqualTo(ReviewStatus.REQUIRES_REVIEW);
    }

    /* ---------------------------------------------------------------------- */
    /* Helpers                                                                */
    /* ---------------------------------------------------------------------- */

    private AuthPrincipal principal() {
        return new AuthPrincipal(user.getId(), user.getEmail(), user.getDisplayName(),
                Role.EMPLOYEE, employee.getId(), null);
    }

    private LocalDate today() {
        return LocalDate.now(workEventService.scheduleZone(employee));
    }

    private RecordEventRequest request(EventType type, WorkLocation at) {
        return new RecordEventRequest(
                UUID.randomUUID(), type, Instant.now(), Instant.now(),
                at.getLatitude(), at.getLongitude(), 10.0,
                null, null, null, null,
                new DeviceAssertion("credential", "client-data", "authenticator-data", "signature"),
                CaptureMode.ONLINE, null, null, "test");
    }

    private static RecordEventRequest withoutFingerprint(RecordEventRequest source) {
        return new RecordEventRequest(source.eventId(), source.eventType(), source.eventTime(),
                source.deviceTimestamp(), source.latitude(), source.longitude(), source.gpsAccuracyM(),
                source.siteName(), source.destination(), source.destinationLocationId(), source.photoId(),
                null, source.captureMode(), source.syncTimestamp(),
                source.integrityHash(), source.clientAppVersion());
    }

    private static RecordEventRequest withSite(RecordEventRequest source, String siteName) {
        return new RecordEventRequest(source.eventId(), source.eventType(), source.eventTime(),
                source.deviceTimestamp(), source.latitude(), source.longitude(), source.gpsAccuracyM(),
                siteName, source.destination(), source.destinationLocationId(), source.photoId(),
                source.deviceAssertion(), source.captureMode(), source.syncTimestamp(),
                source.integrityHash(), source.clientAppVersion());
    }

    private static RecordEventRequest withDestination(RecordEventRequest source, Destination destination) {
        return new RecordEventRequest(source.eventId(), source.eventType(), source.eventTime(),
                source.deviceTimestamp(), source.latitude(), source.longitude(), source.gpsAccuracyM(),
                source.siteName(), destination, source.destinationLocationId(), source.photoId(),
                source.deviceAssertion(), source.captureMode(), source.syncTimestamp(),
                source.integrityHash(), source.clientAppVersion());
    }

    private static RecordEventRequest offline(RecordEventRequest source) {
        return new RecordEventRequest(source.eventId(), source.eventType(), source.eventTime(),
                source.deviceTimestamp(), source.latitude(), source.longitude(), source.gpsAccuracyM(),
                source.siteName(), source.destination(), source.destinationLocationId(), source.photoId(),
                source.deviceAssertion(), CaptureMode.OFFLINE, Instant.now(),
                source.integrityHash(), source.clientAppVersion());
    }

    private static RecordEventRequest at(RecordEventRequest source, Instant eventTime) {
        return new RecordEventRequest(source.eventId(), source.eventType(), eventTime,
                eventTime, source.latitude(), source.longitude(), source.gpsAccuracyM(),
                source.siteName(), source.destination(), source.destinationLocationId(), source.photoId(),
                source.deviceAssertion(), source.captureMode(), source.syncTimestamp(),
                source.integrityHash(), source.clientAppVersion());
    }
}
