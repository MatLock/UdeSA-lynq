--liquibase formatted sql

--changeset lynq:12-create-job-daily-stats
CREATE TABLE IF NOT EXISTS lynq_analytics_db.job_daily_stats (
    snapshot_on       DATE         NOT NULL,
    category          VARCHAR(64)  NOT NULL,
    open_job_posts    INT          NOT NULL,
    open_with_salary  INT          NOT NULL,
    CONSTRAINT pk_job_daily_stats PRIMARY KEY (snapshot_on, category)
);
