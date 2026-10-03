--liquibase formatted sql

--changeset lynq:10-create-candidate-daily-benchmark
CREATE TABLE IF NOT EXISTS lynq_analytics_db.candidate_daily_benchmark (
    snapshot_on           DATE         NOT NULL,
    candidate_id          VARCHAR(36)  NOT NULL,
    market_fit            SMALLINT,
    jobs_scored           SMALLINT     NOT NULL,
    above_threshold_pct   TINYINT,
    reach_threshold       TINYINT      NOT NULL,
    peer_percentile       TINYINT,
    peer_group_size       SMALLINT     NOT NULL,
    peer_fit_p25          SMALLINT,
    peer_fit_median       SMALLINT,
    peer_fit_p75          SMALLINT,
    skill_coverage_pct    TINYINT,
    peer_coverage_median  TINYINT,
    computed_on           DATETIME(6)  NOT NULL,
    CONSTRAINT pk_candidate_daily_benchmark PRIMARY KEY (snapshot_on, candidate_id),
    INDEX idx_candidate_daily_benchmark_candidate (candidate_id, snapshot_on)
);
