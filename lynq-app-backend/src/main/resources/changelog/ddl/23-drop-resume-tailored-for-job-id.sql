--liquibase formatted sql

--changeset lynq:23-drop-resume-tailored-for-job-id
-- It marked a stored resume as one CV Tailor had written for a posting. Nothing
-- writes it any more: a tailored resume is never stored as one of the
-- candidate's own, it hangs off the application it was made for. The column had
-- no reader left either, so it only promised a value that was always null.
ALTER TABLE lynq_backend_db.user_resumes
    DROP COLUMN tailored_for_job_id;
