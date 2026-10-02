--liquibase formatted sql

--changeset lynq:25-add-job-post-liveness
ALTER TABLE lynq_backend_db.job_posts
    ADD COLUMN last_seen_on DATE NULL AFTER closed_on,
    ADD COLUMN last_checked_on DATE NULL AFTER last_seen_on,
    ADD COLUMN close_reason VARCHAR(32) NULL AFTER last_checked_on;

UPDATE lynq_backend_db.job_posts
SET last_seen_on = created_on
WHERE job_post_source <> 'LYNQ';
