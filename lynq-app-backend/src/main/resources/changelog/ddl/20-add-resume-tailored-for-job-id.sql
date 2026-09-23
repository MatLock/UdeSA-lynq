--liquibase formatted sql

--changeset lynq:20-add-resume-tailored-for-job-id
ALTER TABLE lynq_backend_db.user_resumes
    ADD COLUMN tailored_for_job_id VARCHAR(64) NULL AFTER alias;
