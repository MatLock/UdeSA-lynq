--liquibase formatted sql

--changeset lynq:05-create-candidates
CREATE TABLE IF NOT EXISTS lynq_analytics_db.candidates (
    id                       VARCHAR(36)  NOT NULL,
    expected_salary          INT,
    expected_salary_currency CHAR(3),
    synthetic                BOOLEAN      NOT NULL DEFAULT FALSE,
    skills_occurred_on       DATETIME(6),
    salary_occurred_on       DATETIME(6),
    CONSTRAINT pk_candidates PRIMARY KEY (id)
);
