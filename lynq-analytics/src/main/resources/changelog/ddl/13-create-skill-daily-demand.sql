--liquibase formatted sql

--changeset lynq:13-create-skill-daily-demand
CREATE TABLE IF NOT EXISTS lynq_analytics_db.skill_daily_demand (
    snapshot_on     DATE         NOT NULL,
    skill           VARCHAR(255) NOT NULL,
    open_job_posts  INT          NOT NULL,
    CONSTRAINT pk_skill_daily_demand PRIMARY KEY (snapshot_on, skill)
);
