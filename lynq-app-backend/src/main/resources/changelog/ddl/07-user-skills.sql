--liquibase formatted sql

--changeset lynq:07-create-user-skills-table
CREATE TABLE IF NOT EXISTS lynq_backend_db.user_skills (
    id       VARCHAR(36)  NOT NULL,
    user_id  VARCHAR(36)  NOT NULL,
    skill    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_user_skills PRIMARY KEY (id),
    CONSTRAINT fk_user_skills_user FOREIGN KEY (user_id) REFERENCES lynq_backend_db.users (id),
    CONSTRAINT uq_user_skills UNIQUE (user_id, skill)
);