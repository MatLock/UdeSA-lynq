--liquibase formatted sql

--changeset lynq:06-create-candidate-skills
CREATE TABLE IF NOT EXISTS lynq_analytics_db.candidate_skills (
    candidate_id  VARCHAR(36)  NOT NULL,
    skill         VARCHAR(255) NOT NULL,
    CONSTRAINT pk_candidate_skills PRIMARY KEY (candidate_id, skill),
    CONSTRAINT fk_candidate_skills_candidate FOREIGN KEY (candidate_id) REFERENCES lynq_analytics_db.candidates (id)
);
