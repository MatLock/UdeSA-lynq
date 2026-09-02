--liquibase formatted sql

--changeset lynq:15-backfill-user-skills-from-resumes failOnError:false
--comment: Until now nothing ever wrote user_skills, so every candidate scored 0 against every job.
--comment: Creating a resume fills the table from now on; this recovers the resumes already stored.
--comment: Only the technical skills and the tools are taken — a soft skill is not a job requirement.
--comment: The capability tags cannot be recovered: they did not exist when these resumes were
--comment: written, and are derived by lynq-ml the next time the candidate runs the extraction.
--comment: failOnError is off on purpose: this recovers history, and must never keep the service
--comment: from starting if a stored resume has a shape these paths do not fit.
INSERT IGNORE INTO lynq_backend_db.user_skills (id, user_id, skill)
SELECT UUID(), resumes.user_id, TRIM(extracted.skill)
FROM lynq_backend_db.user_resumes resumes,
     JSON_TABLE(
         COALESCE(JSON_EXTRACT(resumes.resume, '$.skills.technical'), JSON_ARRAY()),
         '$[*]' COLUMNS (skill VARCHAR(255) PATH '$')
     ) AS extracted
WHERE extracted.skill IS NOT NULL
  AND TRIM(extracted.skill) <> '';

INSERT IGNORE INTO lynq_backend_db.user_skills (id, user_id, skill)
SELECT UUID(), resumes.user_id, TRIM(extracted.tool)
FROM lynq_backend_db.user_resumes resumes,
     JSON_TABLE(
         COALESCE(JSON_EXTRACT(resumes.resume, '$.skills.tools'), JSON_ARRAY()),
         '$[*]' COLUMNS (tool VARCHAR(255) PATH '$')
     ) AS extracted
WHERE extracted.tool IS NOT NULL
  AND TRIM(extracted.tool) <> '';
