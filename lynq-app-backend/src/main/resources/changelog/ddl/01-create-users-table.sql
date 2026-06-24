--liquibase formatted sql

--changeset lynq:01-create-user-table
CREATE TABLE IF NOT EXISTS lynq_backend_db.users (
    id                 VARCHAR(36)  NOT NULL,
    type               ENUM('CANDIDATE', 'COMPANY') NOT NULL,
    profile_image_url  VARCHAR(2048),
    current_position   VARCHAR(255),
    about              TEXT,
    github_url         VARCHAR(2048),
    linkedin_url       VARCHAR(2048),
    birth_date         DATE,
    created_on         DATE         NOT NULL DEFAULT (CURRENT_DATE),
    CONSTRAINT pk_users PRIMARY KEY (id)
);