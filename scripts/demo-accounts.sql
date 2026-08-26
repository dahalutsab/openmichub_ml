-- Demo accounts for viewing the three dashboards.
--
-- The database had only ARTIST accounts and one SUPER_ADMIN whose password is
-- not the seed password, so the admin and booker dashboards had no account you
-- could sign in as. These fill that gap.
--
-- Everything created here is tagged @demo.openmichub.local. The teardown at the
-- bottom of this file removes all of it and touches nothing else.
--
-- Password for both accounts is Admin@123 (same bcrypt hash the seed script uses).
--
-- Phone numbers deliberately sit outside the 9800000001-9800000320 block the
-- seeded artists occupy: phone_number has its own unique constraint, so an
-- ON CONFLICT on email alone does not protect against a collision there.

BEGIN;

INSERT INTO users (email, full_name, password, is_active, is_verified, created_date, location, phone_number)
VALUES
  ('admin@demo.openmichub.local',  'Demo Admin',  '$2a$10$54gPf2W4sUQAMD8nr8Moqe8ylugwB/AXanMm2vbPUlGLaBCsCTNm6', true, true, now() - interval '200 days', 'Kathmandu', '9779810000101'),
  ('booker@demo.openmichub.local', 'Demo Booker', '$2a$10$54gPf2W4sUQAMD8nr8Moqe8ylugwB/AXanMm2vbPUlGLaBCsCTNm6', true, true, now() - interval '200 days', 'Kathmandu', '9779810000102')
ON CONFLICT (email) DO NOTHING;

-- Roles: 2 = ADMIN, 4 = ORGANIZER
INSERT INTO users_roles (user_entity_id, roles_id)
SELECT u.id, 2 FROM users u WHERE u.email = 'admin@demo.openmichub.local';

INSERT INTO users_roles (user_entity_id, roles_id)
SELECT u.id, 4 FROM users u WHERE u.email = 'booker@demo.openmichub.local';

-- ---------------------------------------------------------------------------
-- Bookings for the demo booker.
--
-- New rows rather than reassigning existing ones, so nothing already in the
-- database changes owner. Dates are spread across the last ~5 months and into
-- the next 3 weeks, so the spend series, the status donut, the event-type
-- breakdown, and both the upcoming and past tables all have something in them.
-- ---------------------------------------------------------------------------
INSERT INTO booking (event_date, start_time, end_time, status, venue, event_type, total_amount, artist_id, user_id, created_date, created_by)
SELECT
  d.event_date,
  d.start_time,
  d.start_time + interval '3 hours',
  d.status,
  d.venue,
  d.event_type,
  d.amount,
  a.id,
  (SELECT id FROM users WHERE email = 'booker@demo.openmichub.local'),
  d.event_date - interval '10 days',
  'booker@demo.openmichub.local'
FROM (
  VALUES
    -- past, settled
    (CURRENT_DATE - 140, TIME '19:00', 'COMPLETED', 'Purple Haze Rock Bar', 'Concert',        24500.0, 1),
    (CURRENT_DATE - 126, TIME '20:00', 'COMPLETED', 'Jatra Cafe',           'Live Session',   18200.0, 2),
    (CURRENT_DATE - 118, TIME '18:00', 'COMPLETED', 'Moksh Live',           'Open Mic',        9800.0, 3),
    (CURRENT_DATE -  97, TIME '21:00', 'COMPLETED', 'House of Music',       'Concert',        31000.0, 4),
    (CURRENT_DATE -  88, TIME '19:30', 'CANCELLED', 'Trisara',              'Private Party',  14500.0, 5),
    (CURRENT_DATE -  74, TIME '20:00', 'COMPLETED', 'Purple Haze Rock Bar', 'Concert',        27300.0, 6),
    (CURRENT_DATE -  63, TIME '18:30', 'COMPLETED', 'LOD Thamel',           'Comedy Night',   11200.0, 7),
    (CURRENT_DATE -  52, TIME '19:00', 'COMPLETED', 'Jatra Cafe',           'Live Session',   16800.0, 8),
    (CURRENT_DATE -  44, TIME '22:00', 'COMPLETED', 'Club 25 Hours',        'DJ Set',         22000.0, 9),
    (CURRENT_DATE -  38, TIME '19:00', 'DECLINED',  'Moksh Live',           'Open Mic',        8600.0, 10),
    (CURRENT_DATE -  31, TIME '20:30', 'COMPLETED', 'House of Music',       'Concert',        29800.0, 11),
    (CURRENT_DATE -  24, TIME '18:00', 'COMPLETED', 'Cafe de Patan',        'Live Session',   12400.0, 12),
    (CURRENT_DATE -  17, TIME '21:00', 'COMPLETED', 'Purple Haze Rock Bar', 'Concert',        26500.0, 13),
    (CURRENT_DATE -  12, TIME '19:00', 'COMPLETED', 'LOD Thamel',           'Comedy Night',   10900.0, 14),
    (CURRENT_DATE -   9, TIME '20:00', 'CANCELLED', 'Trisara',              'Private Party',  19500.0, 15),
    (CURRENT_DATE -   5, TIME '18:30', 'COMPLETED', 'Jatra Cafe',           'Live Session',   15600.0, 16),
    -- upcoming
    (CURRENT_DATE +   2, TIME '19:00', 'CONFIRMED', 'House of Music',       'Concert',        28900.0, 17),
    (CURRENT_DATE +   5, TIME '20:00', 'CONFIRMED', 'Purple Haze Rock Bar', 'Concert',        25400.0, 18),
    (CURRENT_DATE +   9, TIME '18:00', 'PENDING',   'Moksh Live',           'Open Mic',        9200.0, 19),
    (CURRENT_DATE +  13, TIME '21:30', 'CONFIRMED', 'Club 25 Hours',        'DJ Set',         23800.0, 20),
    (CURRENT_DATE +  16, TIME '19:00', 'PENDING',   'Cafe de Patan',        'Live Session',   13100.0, 21),
    (CURRENT_DATE +  21, TIME '20:00', 'CONFIRMED', 'LOD Thamel',           'Comedy Night',   12700.0, 22)
) AS d(event_date, start_time, status, venue, event_type, amount, artist_seq)
JOIN LATERAL (
  -- Spread across real artists so "artists you book most" has names in it.
  SELECT id FROM artists ORDER BY id OFFSET (d.artist_seq % 40) LIMIT 1
) a ON true;

-- Payments against the settled bookings, so "payments by method" is populated.
INSERT INTO payment (payment_time, received_amount, total_amount, system_charges,
                     payment_method, payment_status, payment_type, product_code,
                     booking_id, user_info_entity_id, created_date, created_by)
SELECT
  b.start_time,
  b.total_amount,
  b.total_amount,
  round((b.total_amount * 0.045)::numeric, 2),
  CASE WHEN b.id % 3 = 0 THEN 'ESEWA' ELSE 'KHALTI' END,
  'COMPLETED',
  'FULL',
  'DEMO-' || b.id,
  b.id,
  b.user_id,
  b.event_date + interval '1 hour',
  'booker@demo.openmichub.local'
FROM booking b
JOIN users u ON u.id = b.user_id
WHERE u.email = 'booker@demo.openmichub.local'
  AND b.status = 'COMPLETED';

COMMIT;

SELECT u.email, r.name AS role,
       (SELECT count(*) FROM booking b WHERE b.user_id = u.id) AS bookings
FROM users u
JOIN users_roles ur ON ur.user_entity_id = u.id
JOIN roles r ON r.id = ur.roles_id
WHERE u.email LIKE '%@demo.openmichub.local';

-- ---------------------------------------------------------------------------
-- TEARDOWN - removes everything this file created, and nothing else:
--
-- DELETE FROM payment WHERE created_by = 'booker@demo.openmichub.local';
-- DELETE FROM booking WHERE created_by = 'booker@demo.openmichub.local';
-- DELETE FROM users_roles WHERE user_entity_id IN
--   (SELECT id FROM users WHERE email LIKE '%@demo.openmichub.local');
-- DELETE FROM users WHERE email LIKE '%@demo.openmichub.local';
-- ---------------------------------------------------------------------------
