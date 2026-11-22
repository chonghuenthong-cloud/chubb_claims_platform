-- Demo data for local development. Kept out of db/migration so it never runs in other environments
-- (only loaded when spring.flyway.locations includes classpath:db/seed).
-- Every demo user's password is: Password123!

-- pgcrypto's crypt() produces BCrypt hashes ($2a$) that Spring Security's BCryptPasswordEncoder can verify
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Users: two claimants, two staff. Fixed UUIDs make the data easy to reference in tests and API calls.
INSERT INTO app_user (id, email, full_name, role, password_hash) VALUES
  ('00000000-0000-0000-0000-000000000001', 'alice.tan@example.com',   'Alice Tan',   'CLAIMANT', crypt('Password123!', gen_salt('bf', 10))),
  ('00000000-0000-0000-0000-000000000002', 'ben.lim@example.com',     'Ben Lim',     'CLAIMANT', crypt('Password123!', gen_salt('bf', 10))),
  ('00000000-0000-0000-0000-000000000011', 'priya.nair@example.com',  'Priya Nair',  'STAFF',    crypt('Password123!', gen_salt('bf', 10))),
  ('00000000-0000-0000-0000-000000000012', 'daniel.wong@example.com', 'Daniel Wong', 'STAFF',    crypt('Password123!', gen_salt('bf', 10)));

INSERT INTO policy (id, policy_number, holder_id, market, product_type, coverage_limit, currency, start_date, end_date) VALUES
  ('00000000-0000-0000-0000-000000000101', 'SG-MOT-0001', '00000000-0000-0000-0000-000000000001', 'SG', 'MOTOR',     50000.00, 'SGD', '2026-01-01', '2027-01-01'),
  ('00000000-0000-0000-0000-000000000102', 'SG-PRP-0002', '00000000-0000-0000-0000-000000000001', 'SG', 'PROPERTY', 500000.00, 'SGD', '2026-03-01', '2027-03-01'),
  ('00000000-0000-0000-0000-000000000103', 'MY-MOT-0003', '00000000-0000-0000-0000-000000000002', 'MY', 'MOTOR',    120000.00, 'MYR', '2026-02-15', '2027-02-15');

-- One claim in each main state, so the work queue and dashboard have something to show
INSERT INTO claim (id, claim_number, policy_id, claimant_id, assigned_officer_id, status, incident_date,
                   incident_description, estimated_loss, reserve_amount, approved_amount, rejection_reason, submitted_at) VALUES
  ('00000000-0000-0000-0000-000000001001', 'CLM-000001', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001',
   NULL, 'SUBMITTED', '2026-11-18', 'Rear-ended at a traffic light on Orchard Road. Bumper and boot damaged.',
   4200.00, 4200.00, NULL, NULL, '2026-11-19 09:15:00+08'),
  ('00000000-0000-0000-0000-000000001002', 'CLM-000002', '00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000011', 'UNDER_REVIEW', '2026-11-10', 'Side collision in a car park. Driver door and wing mirror replaced.',
   15000.00, 12000.00, NULL, NULL, '2026-11-11 14:02:00+08'),
  ('00000000-0000-0000-0000-000000001003', 'CLM-000003', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000012', 'AWAITING_INFO', '2026-11-02', 'Burst pipe flooded the kitchen. Cabinets and flooring damaged.',
   30000.00, 25000.00, NULL, NULL, '2026-11-03 08:40:00+08'),
  ('00000000-0000-0000-0000-000000001004', 'CLM-000004', '00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000011', 'APPROVED', '2026-10-20', 'Windscreen cracked by debris on the highway.',
   8500.00, 8000.00, 8000.00, NULL, '2026-10-21 10:30:00+08'),
  ('00000000-0000-0000-0000-000000001005', 'CLM-000005', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000012', 'REJECTED', '2025-12-15', 'Scratches found on the car after parking overnight.',
   1200.00, 0.00, NULL, 'Incident date is before the policy start date.', '2026-10-05 16:20:00+08');

-- Continue numbering after the seeded claims
SELECT setval('claim_number_seq', 5);

-- Status history that leads to each claim's current state
INSERT INTO claim_status_history (id, claim_id, from_status, to_status, changed_by, note, changed_at) VALUES
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001001', NULL,           'SUBMITTED',     '00000000-0000-0000-0000-000000000001', NULL, '2026-11-19 09:15:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001002', NULL,           'SUBMITTED',     '00000000-0000-0000-0000-000000000002', NULL, '2026-11-11 14:02:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001002', 'SUBMITTED',    'UNDER_REVIEW',  '00000000-0000-0000-0000-000000000011', NULL, '2026-11-12 09:00:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001003', NULL,           'SUBMITTED',     '00000000-0000-0000-0000-000000000001', NULL, '2026-11-03 08:40:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001003', 'SUBMITTED',    'UNDER_REVIEW',  '00000000-0000-0000-0000-000000000012', NULL, '2026-11-04 10:00:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001003', 'UNDER_REVIEW', 'AWAITING_INFO', '00000000-0000-0000-0000-000000000012', 'Requested plumber report.', '2026-11-05 11:30:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001004', NULL,           'SUBMITTED',     '00000000-0000-0000-0000-000000000002', NULL, '2026-10-21 10:30:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001004', 'SUBMITTED',    'UNDER_REVIEW',  '00000000-0000-0000-0000-000000000011', NULL, '2026-10-22 09:10:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001004', 'UNDER_REVIEW', 'APPROVED',      '00000000-0000-0000-0000-000000000011', NULL, '2026-10-25 15:45:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001005', NULL,           'SUBMITTED',     '00000000-0000-0000-0000-000000000001', NULL, '2026-10-05 16:20:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001005', 'SUBMITTED',    'UNDER_REVIEW',  '00000000-0000-0000-0000-000000000012', NULL, '2026-10-06 09:30:00+08'),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001005', 'UNDER_REVIEW', 'REJECTED',      '00000000-0000-0000-0000-000000000012', 'Incident date is before the policy start date.', '2026-10-06 10:05:00+08');

-- The open question that keeps CLM-000003 in AWAITING_INFO
INSERT INTO information_request (id, claim_id, requested_by, question, requested_at) VALUES
  (gen_random_uuid(), '00000000-0000-0000-0000-000000001003', '00000000-0000-0000-0000-000000000012',
   'Please upload the plumber''s report confirming the cause of the burst pipe.', '2026-11-05 11:30:00+08');
