package com.uniace.trackr.domain;

import com.uniace.trackr.domain.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * The central record of the system: one verified work activity.
 *
 * <p>The identifier is generated on the employee's device, not by the database.
 * That is what makes offline synchronisation idempotent - re-sending a queued
 * event can never produce a second row (requirement 40).
 *
 * <p>Four separate times are kept so that an auditor can reconstruct what
 * happened: {@code eventTime} (when the employee says it happened),
 * {@code deviceTimestamp} (the phone's own clock at capture),
 * {@code syncTimestamp} (when the device uploaded it) and {@code receivedAt}
 * (when the server processed it).
 */
@Entity
@Table(name = "work_events")
public class WorkEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "device_timestamp")
    private Instant deviceTimestamp;

    /** The employee's local calendar date for this event, in the schedule timezone. */
    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    private Double latitude;

    private Double longitude;

    @Column(name = "gps_accuracy_m")
    private Double gpsAccuracyM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private WorkLocation location;

    /**
     * The warehouse or site the employee typed at Field Arrival.
     *
     * <p>Free text on purpose. Uni-Ace's warehouses are not a fixed list, and an
     * employee standing at one that nobody has added to the system yet must
     * still be able to record that they arrived.
     */
    @Column(name = "site_name", length = 120)
    private String siteName;

    @Column(name = "distance_to_location_m")
    private Double distanceToLocationM;

    @Column(name = "within_geofence")
    private Boolean withinGeofence;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Destination destination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private WorkLocation destinationLocation;

    @Column(name = "biometric_verified", nullable = false)
    private boolean biometricVerified;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credential_id")
    private DeviceCredential credential;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", unique = true)
    private PhotoEvidence photo;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_mode", nullable = false, length = 20)
    private CaptureMode captureMode = CaptureMode.ONLINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    private SyncStatus syncStatus = SyncStatus.SYNCED;

    @Column(name = "sync_timestamp")
    private Instant syncTimestamp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Disposition disposition = Disposition.ACCEPTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private UserAccount reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    /**
     * Hash the device computed over the event payload before queueing it. It
     * cannot prove the payload is truthful - a determined user controls their
     * own browser - but a mismatch on arrival is a strong tamper signal.
     */
    @Column(name = "integrity_hash", length = 120)
    private String integrityHash;

    @Column(name = "client_app_version", length = 40)
    private String clientAppVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "workEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<WorkEventFlag> flags = new ArrayList<>();

    @Transient
    private boolean newRecord = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        this.newRecord = false;
    }

    public void addFlag(WorkEventFlag flag) {
        flag.setWorkEvent(this);
        this.flags.add(flag);
    }

    public boolean isFlagged() {
        return disposition == Disposition.FLAGGED;
    }

    public void setId(UUID id) { this.id = id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }


    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }

    public Instant getDeviceTimestamp() { return deviceTimestamp; }
    public void setDeviceTimestamp(Instant deviceTimestamp) { this.deviceTimestamp = deviceTimestamp; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getGpsAccuracyM() { return gpsAccuracyM; }
    public void setGpsAccuracyM(Double gpsAccuracyM) { this.gpsAccuracyM = gpsAccuracyM; }

    public WorkLocation getLocation() { return location; }
    public void setLocation(WorkLocation location) { this.location = location; }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public Double getDistanceToLocationM() { return distanceToLocationM; }
    public void setDistanceToLocationM(Double distanceToLocationM) { this.distanceToLocationM = distanceToLocationM; }

    public Boolean getWithinGeofence() { return withinGeofence; }
    public void setWithinGeofence(Boolean withinGeofence) { this.withinGeofence = withinGeofence; }

    public Destination getDestination() { return destination; }
    public void setDestination(Destination destination) { this.destination = destination; }

    public WorkLocation getDestinationLocation() { return destinationLocation; }
    public void setDestinationLocation(WorkLocation destinationLocation) { this.destinationLocation = destinationLocation; }

    public boolean isBiometricVerified() { return biometricVerified; }
    public void setBiometricVerified(boolean biometricVerified) { this.biometricVerified = biometricVerified; }

    public DeviceCredential getCredential() { return credential; }
    public void setCredential(DeviceCredential credential) { this.credential = credential; }

    public PhotoEvidence getPhoto() { return photo; }
    public void setPhoto(PhotoEvidence photo) { this.photo = photo; }

    public CaptureMode getCaptureMode() { return captureMode; }
    public void setCaptureMode(CaptureMode captureMode) { this.captureMode = captureMode; }

    public SyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(SyncStatus syncStatus) { this.syncStatus = syncStatus; }

    public Instant getSyncTimestamp() { return syncTimestamp; }
    public void setSyncTimestamp(Instant syncTimestamp) { this.syncTimestamp = syncTimestamp; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Disposition getDisposition() { return disposition; }
    public void setDisposition(Disposition disposition) { this.disposition = disposition; }

    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }

    public UserAccount getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UserAccount reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public String getIntegrityHash() { return integrityHash; }
    public void setIntegrityHash(String integrityHash) { this.integrityHash = integrityHash; }

    public String getClientAppVersion() { return clientAppVersion; }
    public void setClientAppVersion(String clientAppVersion) { this.clientAppVersion = clientAppVersion; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<WorkEventFlag> getFlags() { return flags; }
    public void setFlags(List<WorkEventFlag> flags) { this.flags = flags; }
}
