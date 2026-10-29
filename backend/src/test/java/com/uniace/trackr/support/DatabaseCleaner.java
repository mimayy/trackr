package com.uniace.trackr.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empties the database between tests.
 *
 * The synchronisation tests cannot run inside a test-managed transaction: each
 * queued event is stored with {@code REQUIRES_NEW}, and a suspended outer
 * transaction would hide the fixtures from it. So those tests commit for real
 * and clean up here instead, in an order that respects the foreign keys.
 */
@Component
public class DatabaseCleaner {

    private static final String[] TABLES_IN_DELETION_ORDER = {
            "work_event_flags",
            "event_adjustments",
            "work_events",
            "photo_evidence",
            "device_credentials",
            "activation_tokens",
            "refresh_tokens",
            "webauthn_challenges",
            "employees",
            "supervisors",
            "audit_logs",
            "system_settings",
            "user_accounts",
            "locations",
            "work_schedules",
            "holidays",
    };

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void clean() {
        for (String table : TABLES_IN_DELETION_ORDER) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }
}
