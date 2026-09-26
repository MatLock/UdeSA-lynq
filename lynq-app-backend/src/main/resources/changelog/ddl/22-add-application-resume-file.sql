--liquibase formatted sql

--changeset lynq:22-add-user-application-job-resume-file
-- The CV the candidate applied with, as a file, not as a row of user_resumes.
-- A CV Tailor resume is never stored as one of the candidate's own, so
-- user_resume_id is null for those applications and the file is the only way
-- back to the document. resume_name is a snapshot of the label the candidate
-- saw when applying: deleting the resume it came from must not erase what the
-- application was made with.
ALTER TABLE lynq_backend_db.user_application_job
    ADD COLUMN resume_file_storage_id VARCHAR(36) AFTER user_resume_id,
    ADD COLUMN resume_name VARCHAR(255) AFTER resume_file_storage_id;
