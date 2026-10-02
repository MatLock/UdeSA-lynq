--liquibase formatted sql

--changeset lynq:08-create-applications
CREATE TABLE IF NOT EXISTS lynq_analytics_db.applications (
    id            VARCHAR(36)  NOT NULL,
    job_id        VARCHAR(36)  NOT NULL,
    candidate_id  VARCHAR(36)  NOT NULL,
    applied_on    DATE         NOT NULL,
    lynq_score    INT          NOT NULL,
    occurred_on   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_applications PRIMARY KEY (id),
    CONSTRAINT fk_applications_job_post FOREIGN KEY (job_id) REFERENCES lynq_analytics_db.job_posts (id),
    CONSTRAINT uk_applications_job_candidate UNIQUE (job_id, candidate_id),
    INDEX idx_applications_candidate (candidate_id)
);
