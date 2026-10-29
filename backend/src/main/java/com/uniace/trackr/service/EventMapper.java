package com.uniace.trackr.service;

import com.uniace.trackr.domain.*;
import com.uniace.trackr.dto.WorkEventDtos.*;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns entities into the shapes each audience is allowed to see.
 *
 * <p>The employee view is deliberately thinner: no coordinates as numbers, no
 * credential ids, no accuracy readings, no sync internals. Supervisors and
 * administrators get the operational detail they need to judge a record.
 */
@Component
public class EventMapper {

    public EmployeeEventView toEmployeeView(WorkEvent event) {
        return new EmployeeEventView(
                event.getId(),
                event.getEventType(),
                Labels.eventLabel(event.getEventType()),
                event.getEventTime(),
                event.getBusinessDate(),
                event.getLocation() == null ? null : event.getLocation().getName(),
                event.getDestination(),
                destinationName(event),
                event.getLatitude() != null,
                event.isBiometricVerified(),
                event.getPhoto() != null,
                event.getPhoto() == null ? null : event.getPhoto().getId(),
                event.getSyncStatus(),
                event.getCaptureMode(),
                event.getReviewStatus() == com.uniace.trackr.domain.enums.ReviewStatus.REQUIRES_REVIEW,
                event.getLatitude(),
                event.getLongitude(),
                event.getSiteName());
    }

    public StaffEventView toStaffView(WorkEvent event) {
        Employee employee = event.getEmployee();
        return new StaffEventView(
                event.getId(),
                employee.getId(),
                employee.getEmployeeNo(),
                employee.fullName(),
                employee.getBranch(),
                employee.getWorkCategory(),
                event.getEventType(),
                Labels.eventLabel(event.getEventType()),
                event.getEventTime(),
                event.getDeviceTimestamp(),
                event.getSyncTimestamp(),
                event.getReceivedAt(),
                event.getBusinessDate(),
                toLocationView(event.getLocation()),
                event.getLatitude(),
                event.getLongitude(),
                event.getGpsAccuracyM(),
                event.getDistanceToLocationM(),
                event.getWithinGeofence(),
                event.getDestination(),
                destinationName(event),
                event.isBiometricVerified(),
                event.getCredential() == null ? null : event.getCredential().getDeviceLabel(),
                event.getPhoto() != null,
                event.getPhoto() == null ? null : event.getPhoto().getId(),
                event.getCaptureMode(),
                event.getSyncStatus(),
                event.getDisposition(),
                event.getReviewStatus(),
                event.getReviewNotes(),
                event.getSiteName(),
                toFlagViews(event.getFlags()));
    }

    public List<FlagView> toFlagViews(List<WorkEventFlag> flags) {
        if (flags == null) {
            return List.of();
        }
        return flags.stream()
                .map(flag -> new FlagView(
                        flag.getCategory(),
                        flag.getCode(),
                        flag.getSeverity(),
                        Labels.flagLabel(flag.getCode()),
                        flag.getDetail()))
                .toList();
    }

    public LocationView toLocationView(WorkLocation location) {
        if (location == null) {
            return null;
        }
        return new LocationView(
                location.getId(),
                location.getName(),
                location.getType(),
                location.getAddress(),
                location.getLatitude(),
                location.getLongitude(),
                location.getGeofenceRadiusM());
    }


    private static String destinationName(WorkEvent event) {
        if (event.getDestinationLocation() != null) {
            return event.getDestinationLocation().getName();
        }
        return Labels.destinationLabel(event.getDestination());
    }
}
