-- =============================================================================
-- Field assignments are removed, and Field Arrival now records a typed site
-- name instead.
--
-- Why: Uni-Ace's warehouses are not a fixed list. Pre-booking a visit against a
-- known location did not match how the work actually happens, and it also
-- gated the workflow - an employee with no assignment for the day could not
-- record Office Departure at all.
--
-- The geofence goes with it. Distance to the nearest known location is still
-- recorded for information, but nothing is refused on the strength of it.
-- =============================================================================

-- The name of the warehouse or site the employee says they are at. Free text,
-- because the list of warehouses changes and an employee must never be blocked
-- by one that nobody has added yet.
ALTER TABLE work_events ADD COLUMN site_name VARCHAR(120);

-- Carry across whatever the old assignments recorded, so history stays
-- readable rather than becoming a column of nulls.
UPDATE work_events w
SET    site_name = COALESCE(l.name, a.client_name)
FROM   field_assignments a
LEFT   JOIN locations l ON l.id = a.location_id
WHERE  w.assignment_id = a.id
AND    w.event_type = 'FIELD_ARRIVAL';

CREATE INDEX ix_work_events_site_name ON work_events (site_name);

-- Now the assignment link itself can go.
DROP INDEX IF EXISTS ix_work_events_assignment;
ALTER TABLE work_events DROP CONSTRAINT IF EXISTS fk_work_events_assignment;
ALTER TABLE work_events DROP COLUMN assignment_id;

DROP TABLE IF EXISTS field_assignments;

-- An adjustment can no longer target an assignment; it targets the site name.
UPDATE event_adjustments SET field_name = 'SITE_NAME' WHERE field_name = 'ASSIGNMENT';

ALTER TABLE event_adjustments DROP CONSTRAINT IF EXISTS ck_event_adjustments_field;
ALTER TABLE event_adjustments ADD CONSTRAINT ck_event_adjustments_field
    CHECK (field_name IN ('EVENT_TIME', 'LOCATION', 'EVENT_TYPE', 'SITE_NAME', 'REVIEW_NOTE'));

-- Nothing can be flagged as an assignment problem any more.
DELETE FROM work_event_flags WHERE category = 'ASSIGNMENT';

ALTER TABLE work_event_flags DROP CONSTRAINT IF EXISTS ck_work_event_flags_category;
ALTER TABLE work_event_flags ADD CONSTRAINT ck_work_event_flags_category
    CHECK (category IN ('ATTENDANCE', 'LOCATION', 'AUTHENTICATION', 'OFFLINE_SYNC', 'WORKFLOW'));

-- The geofence enforcement switch no longer has anything to enforce.
DELETE FROM system_settings WHERE setting_key = 'geofence.enforce_field_arrival';
