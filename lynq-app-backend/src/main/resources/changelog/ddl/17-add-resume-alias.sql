--liquibase formatted sql

--changeset lynq:17-add-resume-alias
ALTER TABLE lynq_backend_db.user_resumes
    ADD COLUMN alias VARCHAR(100) AFTER name;
