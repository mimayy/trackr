package com.uniace.trackr.service;

import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.domain.WorkLocation;
import com.uniace.trackr.domain.enums.Branch;
import com.uniace.trackr.domain.enums.LocationType;
import com.uniace.trackr.repo.WorkLocationRepository;
import com.uniace.trackr.util.GeoUtil;
import com.uniace.trackr.web.error.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configured locations, and matching a recorded position against them. */
@Service
public class LocationService {

    private final WorkLocationRepository repository;
    private final TrackrProperties properties;

    public LocationService(WorkLocationRepository repository, TrackrProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * How a recorded position relates to a configured location.
     *
     * @param location        the location matched, or null when the position is
     *                        nowhere near any configured site
     * @param distanceMeters  distance to that location
     * @param withinGeofence  whether the position (allowing for its own reported
     *                        accuracy) falls inside the location's geofence
     */
    public record LocationMatch(WorkLocation location, double distanceMeters, boolean withinGeofence) {

        public static LocationMatch none() {
            return new LocationMatch(null, Double.NaN, false);
        }

        public boolean matched() {
            return location != null;
        }
    }

    @Transactional(readOnly = true)
    public List<WorkLocation> listAll() {
        return repository.findAllByOrderByTypeAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<WorkLocation> listActive() {
        return repository.findByActiveTrueOrderByTypeAscNameAsc();
    }

    @Transactional(readOnly = true)
    public WorkLocation require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("That location could not be found."));
    }

    @Transactional(readOnly = true)
    public Optional<WorkLocation> defaultOfficeFor(Branch branch) {
        return repository.findByTypeAndBranchAndActiveTrue(LocationType.OFFICE, branch).stream().findFirst();
    }

    @Transactional
    public WorkLocation save(WorkLocation location) {
        return repository.save(location);
    }

    /**
     * Finds the configured location a position belongs to.
     *
     * <p>The nearest active location wins, and only if it is within its own
     * geofence or at least within a configured slack distance - otherwise the
     * position is reported as matching nothing rather than being snapped to a
     * site kilometres away.
     */
    @Transactional(readOnly = true)
    public LocationMatch resolve(double latitude, double longitude, Double accuracyMeters) {
        WorkLocation nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (WorkLocation candidate : repository.findByActiveTrueOrderByTypeAscNameAsc()) {
            double distance = GeoUtil.distanceMeters(latitude, longitude,
                    candidate.getLatitude(), candidate.getLongitude());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        if (nearest == null) {
            return LocationMatch.none();
        }

        boolean within = GeoUtil.withinGeofence(nearestDistance, nearest.getGeofenceRadiusM(), accuracyMeters);
        double slack = nearest.getGeofenceRadiusM() + properties.getGps().getLocationMatchSlackMeters();
        if (!within && nearestDistance > slack) {
            return LocationMatch.none();
        }

        return new LocationMatch(nearest, GeoUtil.round1(nearestDistance), within);
    }
}
