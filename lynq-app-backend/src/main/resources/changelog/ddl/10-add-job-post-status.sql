--liquibase formatted sql

--changeset lynq:10-add-job-post-status
ALTER TABLE lynq_backend_db.job_posts
    ADD COLUMN job_status VARCHAR(255) NOT NULL DEFAULT 'OPEN' AFTER created_on;