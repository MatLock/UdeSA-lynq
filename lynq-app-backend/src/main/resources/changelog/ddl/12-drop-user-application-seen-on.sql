--liquibase formatted sql

--changeset lynq:12-drop-user-application-seen-on
ALTER TABLE lynq_backend_db.user_application_job
    DROP COLUMN application_seen_on;