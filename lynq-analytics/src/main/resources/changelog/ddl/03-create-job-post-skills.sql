--liquibase formatted sql

--changeset lynq:03-create-job-post-skills
CREATE TABLE IF NOT EXISTS lynq_analytics_db.job_post_skills (
    job_id  VARCHAR(36)  NOT NULL,
    skill   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_job_post_skills PRIMARY KEY (job_id, skill),
    CONSTRAINT fk_job_post_skills_job_post FOREIGN KEY (job_id) REFERENCES lynq_analytics_db.job_posts (id)
);
