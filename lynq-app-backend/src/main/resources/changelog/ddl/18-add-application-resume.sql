--liquibase formatted sql

--changeset lynq:18-add-user-application-job-resume
-- The resume the candidate applied with. Nullable: applications registered
-- before candidates could choose one have none, and the column is what the
-- recruiter's candidate view will read once that is built.
ALTER TABLE lynq_backend_db.user_application_job
    ADD COLUMN user_resume_id VARCHAR(36) AFTER user_id,
    ADD CONSTRAINT fk_user_application_job_resume
        FOREIGN KEY (user_resume_id) REFERENCES lynq_backend_db.user_resumes (id);
