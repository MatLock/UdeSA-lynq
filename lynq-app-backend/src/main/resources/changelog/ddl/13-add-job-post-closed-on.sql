--liquibase formatted sql

--changeset lynq:13-add-job-post-closed-on
ALTER TABLE lynq_backend_db.job_posts
    ADD COLUMN closed_on DATE NULL AFTER created_on;
