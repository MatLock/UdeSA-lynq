--liquibase formatted sql

--changeset lynq:04-create-job-post-skills-table
CREATE TABLE IF NOT EXISTS lynq_backend_db.job_post_skills (
    id      VARCHAR(36)  NOT NULL,
    job_id  VARCHAR(36)  NOT NULL,
    skill   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_job_post_skills PRIMARY KEY (id),
    CONSTRAINT fk_job_post_skills_job_post FOREIGN KEY (job_id) REFERENCES lynq_backend_db.job_posts (id),
    CONSTRAINT uq_job_post_skills UNIQUE (job_id, skill)
);