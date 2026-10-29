-- =============================================================================
-- Reference data: the two Uni-Ace offices, the three warehouses, the default
-- work schedules, Philippine non-working days and default system settings.
--
-- User accounts are deliberately NOT seeded here: passwords must be hashed with
-- the application's password encoder, so they are created at startup by
-- BootstrapService / DemoDataSeeder instead of being hard-coded in SQL.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Locations
-- -----------------------------------------------------------------------------
INSERT INTO locations (id, name, type, branch, address, latitude, longitude, geofence_radius_m, active) VALUES
  ('11111111-0000-4000-a000-000000000001', 'Manila Office', 'OFFICE', 'MANILA',
   '8th Floor, Uni-Ace Building, Ayala Avenue, Makati City, Metro Manila',
   14.554729, 121.024445, 120, TRUE),

  ('11111111-0000-4000-a000-000000000002', 'Cebu Office', 'OFFICE', 'CEBU',
   '4th Floor, Uni-Ace Center, Osmena Boulevard, Cebu City, Cebu',
   10.315699, 123.885437, 120, TRUE),

  ('22222222-0000-4000-a000-000000000001', 'Warehouse 1', 'WAREHOUSE', 'MANILA',
   'Lot 4, Karuhatan Industrial Park, Valenzuela City, Metro Manila',
   14.700100, 120.980300, 150, TRUE),

  ('22222222-0000-4000-a000-000000000002', 'Warehouse 2', 'WAREHOUSE', 'MANILA',
   'Km 18 West Service Road, Paranaque City, Metro Manila',
   14.479500, 121.019800, 150, TRUE),

  ('22222222-0000-4000-a000-000000000003', 'Warehouse 3', 'WAREHOUSE', 'CEBU',
   'Cansaga Road, Mandaue City, Cebu',
   10.333300, 123.941100, 150, TRUE),

  ('33333333-0000-4000-a000-000000000001', 'ABC Pharma Distribution Center', 'FIELD', 'MANILA',
   'Barangay Ugong, Pasig City, Metro Manila',
   14.583400, 121.077200, 120, TRUE),

  ('33333333-0000-4000-a000-000000000002', 'Northgate Cold Storage', 'FIELD', 'MANILA',
   'Northgate Avenue, Alabang, Muntinlupa City, Metro Manila',
   14.418900, 121.031500, 120, TRUE),

  ('33333333-0000-4000-a000-000000000003', 'Mactan Logistics Yard', 'FIELD', 'CEBU',
   'MEPZ 1, Lapu-Lapu City, Cebu',
   10.307800, 123.976400, 120, TRUE);

-- -----------------------------------------------------------------------------
-- Work schedules
-- -----------------------------------------------------------------------------
INSERT INTO work_schedules (id, name, start_time, end_time, working_days, break_start, break_end, break_paid, timezone, active) VALUES
  ('44444444-0000-4000-a000-000000000001', 'Standard Workday',
   '08:00', '17:00', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY',
   '12:00', '13:00', FALSE, 'Asia/Manila', TRUE),

  ('44444444-0000-4000-a000-000000000002', 'Field Early Shift',
   '07:00', '16:00', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY',
   '11:30', '12:30', FALSE, 'Asia/Manila', TRUE),

  ('44444444-0000-4000-a000-000000000003', 'Six-Day Warehouse Shift',
   '08:00', '17:00', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY',
   '12:00', '13:00', FALSE, 'Asia/Manila', TRUE);

-- -----------------------------------------------------------------------------
-- Non-working days (Philippine regular and special non-working holidays, 2026)
-- -----------------------------------------------------------------------------
INSERT INTO holidays (id, holiday_date, name, branch, paid) VALUES
  ('55555555-0000-4000-a000-000000000001', '2026-01-01', 'New Year''s Day',            NULL, TRUE),
  ('55555555-0000-4000-a000-000000000002', '2026-04-02', 'Maundy Thursday',            NULL, TRUE),
  ('55555555-0000-4000-a000-000000000003', '2026-04-03', 'Good Friday',                NULL, TRUE),
  ('55555555-0000-4000-a000-000000000004', '2026-04-09', 'Araw ng Kagitingan',         NULL, TRUE),
  ('55555555-0000-4000-a000-000000000005', '2026-05-01', 'Labor Day',                  NULL, TRUE),
  ('55555555-0000-4000-a000-000000000006', '2026-06-12', 'Independence Day',           NULL, TRUE),
  ('55555555-0000-4000-a000-000000000007', '2026-08-21', 'Ninoy Aquino Day',           NULL, FALSE),
  ('55555555-0000-4000-a000-000000000008', '2026-08-31', 'National Heroes Day',        NULL, TRUE),
  ('55555555-0000-4000-a000-000000000009', '2026-11-01', 'All Saints'' Day',           NULL, FALSE),
  ('55555555-0000-4000-a000-00000000000a', '2026-11-30', 'Bonifacio Day',              NULL, TRUE),
  ('55555555-0000-4000-a000-00000000000b', '2026-12-08', 'Feast of the Immaculate Conception', NULL, FALSE),
  ('55555555-0000-4000-a000-00000000000c', '2026-12-25', 'Christmas Day',              NULL, TRUE),
  ('55555555-0000-4000-a000-00000000000d', '2026-12-30', 'Rizal Day',                  NULL, TRUE),
  ('55555555-0000-4000-a000-00000000000e', '2026-12-31', 'Last Day of the Year',       NULL, FALSE),
  ('55555555-0000-4000-a000-00000000000f', '2026-01-16', 'Sinulog Festival',           'CEBU', FALSE);

-- -----------------------------------------------------------------------------
-- Default system settings (administrator editable at runtime)
-- -----------------------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, description) VALUES
  ('gps.max_accuracy_meters', '150',
   'A position less accurate than this cannot verify a work location.'),
  ('gps.warn_accuracy_meters', '50',
   'A position less accurate than this is recorded but flagged for review.'),
  ('geofence.enforce_field_arrival', 'true',
   'Refuse a Field Arrival recorded outside the assigned geofence.'),
  ('attendance.late_grace_minutes', '0',
   'Minutes after the scheduled start before a Time In counts as late.'),
  ('attendance.overtime_threshold_minutes', '30',
   'Minutes worked beyond the schedule before overtime is counted.'),
  ('attendance.max_workday_hours', '16',
   'A workday longer than this is treated as an impossible duration and flagged.'),
  ('sync.max_clock_skew_minutes', '10',
   'Allowed difference between device clock and server clock for offline events.'),
  ('photo.require_for_field_arrival', 'false',
   'Whether photo evidence is mandatory for Field Arrival. Optional by default.'),
  ('retention.work_event_days', '1095',
   'How long work events are retained. 0 keeps them forever.'),
  ('retention.photo_days', '365',
   'How long photo evidence is retained. 0 keeps it forever.');
