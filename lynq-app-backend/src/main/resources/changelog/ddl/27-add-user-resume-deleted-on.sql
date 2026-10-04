--liquibase formatted sql

--changeset lynq:27-add-user-resume-deleted-on
ALTER TABLE lynq_backend_db.user_resumes
    ADD COLUMN deleted_on DATE NULL AFTER created_on;
