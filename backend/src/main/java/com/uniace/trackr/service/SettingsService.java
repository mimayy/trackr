package com.uniace.trackr.service;

import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.domain.SystemSetting;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.repo.SystemSettingRepository;
import com.uniace.trackr.service.attendance.AttendanceRules;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * System behaviour the administrator can change at runtime.
 *
 * <p>Values are read from the {@code system_settings} table and fall back to
 * the environment-driven defaults in {@link TrackrProperties} when a key is
 * absent or unparseable, so a bad edit degrades to the configured default
 * rather than breaking event capture.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    public static final String GPS_MAX_ACCURACY = "gps.max_accuracy_meters";
    public static final String GPS_WARN_ACCURACY = "gps.warn_accuracy_meters";
    public static final String GEOFENCE_ENFORCE_FIELD_ARRIVAL = "geofence.enforce_field_arrival";
    public static final String LATE_GRACE_MINUTES = "attendance.late_grace_minutes";
    public static final String OVERTIME_THRESHOLD_MINUTES = "attendance.overtime_threshold_minutes";
    public static final String MAX_WORKDAY_HOURS = "attendance.max_workday_hours";
    public static final String MAX_CLOCK_SKEW_MINUTES = "sync.max_clock_skew_minutes";

    private final SystemSettingRepository repository;
    private final TrackrProperties properties;

    public SettingsService(SystemSettingRepository repository, TrackrProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> all() {
        return repository.findAllByOrderByKeyAsc();
    }

    @Transactional
    public SystemSetting update(String key, String value, UserAccount actor) {
        SystemSetting setting = repository.findById(key)
                .orElseGet(() -> {
                    SystemSetting created = new SystemSetting();
                    created.setKey(key);
                    return created;
                });
        setting.setValue(value);
        setting.setUpdatedBy(actor);
        return repository.save(setting);
    }

    public double gpsMaxAccuracyMeters() {
        return doubleSetting(GPS_MAX_ACCURACY, properties.getGps().getMaxAccuracyMeters());
    }

    public double gpsWarnAccuracyMeters() {
        return doubleSetting(GPS_WARN_ACCURACY, properties.getGps().getWarnAccuracyMeters());
    }

    public int maxClockSkewMinutes() {
        return intSetting(MAX_CLOCK_SKEW_MINUTES, properties.getSync().getMaxClockSkewMinutes());
    }

    public AttendanceRules attendanceRules() {
        return new AttendanceRules(
                intSetting(LATE_GRACE_MINUTES, properties.getAttendance().getLateGraceMinutes()),
                intSetting(OVERTIME_THRESHOLD_MINUTES, properties.getAttendance().getOvertimeThresholdMinutes()),
                intSetting(MAX_WORKDAY_HOURS, properties.getAttendance().getMaxWorkdayHours()));
    }

    private Optional<String> raw(String key) {
        try {
            return repository.findById(key).map(SystemSetting::getValue);
        } catch (RuntimeException ex) {
            // Never let a settings lookup take down event capture.
            log.warn("Could not read system setting {}, using the configured default", key, ex);
            return Optional.empty();
        }
    }

    private double doubleSetting(String key, double fallback) {
        return raw(key).map(value -> {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException ex) {
                log.warn("System setting {} is not a number ({}), using {}", key, value, fallback);
                return fallback;
            }
        }).orElse(fallback);
    }

    private int intSetting(String key, int fallback) {
        return raw(key).map(value -> {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                log.warn("System setting {} is not a whole number ({}), using {}", key, value, fallback);
                return fallback;
            }
        }).orElse(fallback);
    }

    private boolean booleanSetting(String key, boolean fallback) {
        return raw(key).map(value -> "true".equalsIgnoreCase(value.trim())).orElse(fallback);
    }
}
