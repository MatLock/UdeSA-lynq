-- Backfill of lynq_iam_db.user_roles from lynq_backend_db.users.type.
-- One-off, run by hand once per environment. Not a Liquibase changeset: it reads
-- another service's schema and must not become a permanent dependency of the IAM
-- startup. Run it after the 02-create-user-roles-table changeset has been applied
-- and before lynq_backend_db.users.type is dropped.

-- 1. Diagnostics: how many IAM users have no counterpart in the backend
SELECT COUNT(*) AS huerfanos
FROM lynq_iam_db.users i
LEFT JOIN lynq_backend_db.users b ON b.id = i.id
WHERE b.id IS NULL;

-- 2. Backfill. The derived table is what makes it re-runnable: MySQL 8.4 removed
-- VALUES() from ON DUPLICATE KEY UPDATE, so the inserted row is named instead.
INSERT INTO lynq_iam_db.user_roles (user_id, role)
SELECT * FROM (
    SELECT b.id AS user_id,
           CASE b.type WHEN 'COMPANY' THEN 'R_COMPANY' ELSE 'R_CANDIDATE' END AS role
    FROM lynq_backend_db.users b
    JOIN lynq_iam_db.users i ON i.id = b.id
) AS backfill
ON DUPLICATE KEY UPDATE role = backfill.role;

-- 3. Verification: no IAM user should be left without a role
SELECT i.id, i.username
FROM lynq_iam_db.users i
LEFT JOIN lynq_iam_db.user_roles r ON r.user_id = i.id
WHERE r.user_id IS NULL;
