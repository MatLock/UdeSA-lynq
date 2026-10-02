--liquibase formatted sql

--changeset lynq:02-create-job-posts
CREATE TABLE IF NOT EXISTS lynq_analytics_db.job_posts (
    id                  VARCHAR(36)  NOT NULL,
    title               VARCHAR(255) NOT NULL,
    category            VARCHAR(64),
    work_type           VARCHAR(32)  NOT NULL,
    source              VARCHAR(32)  NOT NULL,
    company_id          VARCHAR(36),
    created_by_user_id  VARCHAR(36),
    salary_range_down   INT,
    salary_range_top    INT,
    salary_currency     CHAR(3),
    status              VARCHAR(16)  NOT NULL,
    published_on        DATE         NOT NULL,
    closed_on           DATE,
    close_reason        VARCHAR(32),
    reopened_on         DATE,
    details_occurred_on DATETIME(6)  NOT NULL,
    status_occurred_on  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_job_posts PRIMARY KEY (id)
);
