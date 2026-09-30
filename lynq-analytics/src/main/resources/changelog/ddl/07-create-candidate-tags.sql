--liquibase formatted sql

--changeset lynq:07-create-candidate-tags
CREATE TABLE IF NOT EXISTS lynq_analytics_db.candidate_tags (
    candidate_id  VARCHAR(36)  NOT NULL,
    tag           VARCHAR(255) NOT NULL,
    CONSTRAINT pk_candidate_tags PRIMARY KEY (candidate_id, tag),
    CONSTRAINT fk_candidate_tags_candidate FOREIGN KEY (candidate_id) REFERENCES lynq_analytics_db.candidates (id),
    INDEX idx_candidate_tags_tag (tag)
);
