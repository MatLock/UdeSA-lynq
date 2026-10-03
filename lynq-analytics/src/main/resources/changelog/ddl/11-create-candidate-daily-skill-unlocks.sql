--liquibase formatted sql

--changeset lynq:11-create-candidate-daily-skill-unlocks
CREATE TABLE IF NOT EXISTS lynq_analytics_db.candidate_daily_skill_unlocks (
    snapshot_on    DATE         NOT NULL,
    candidate_id   VARCHAR(36)  NOT NULL,
    skill          VARCHAR(255) NOT NULL,
    jobs_unlocked  SMALLINT     NOT NULL,
    CONSTRAINT pk_candidate_daily_skill_unlocks PRIMARY KEY (snapshot_on, candidate_id, skill),
    CONSTRAINT fk_candidate_daily_skill_unlocks_benchmark FOREIGN KEY (snapshot_on, candidate_id)
        REFERENCES lynq_analytics_db.candidate_daily_benchmark (snapshot_on, candidate_id)
);
