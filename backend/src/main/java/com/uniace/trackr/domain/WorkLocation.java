package com.uniace.trackr.domain;

import com.uniace.trackr.domain.enums.Branch;
import com.uniace.trackr.domain.enums.LocationType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A configured work location - an office, a warehouse or another field site -
 * together with the geofence used to verify arrivals.
 */
@Entity
@Table(name = "locations")
public class WorkLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Branch branch;

    @Column(length = 400)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "geofence_radius_m", nullable = false)
    private int geofenceRadiusM = 100;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocationType getType() { return type; }
    public void setType(LocationType type) { this.type = type; }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getGeofenceRadiusM() { return geofenceRadiusM; }
    public void setGeofenceRadiusM(int geofenceRadiusM) { this.geofenceRadiusM = geofenceRadiusM; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
