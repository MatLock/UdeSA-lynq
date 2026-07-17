--liquibase formatted sql

--changeset lynq:11-add-job-post-total-seen
ALTER TABLE lynq_backend_db.job_posts
    ADD COLUMN total_seen BIGINT NOT NULL DEFAULT 0;