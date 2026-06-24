--liquibase formatted sql

--changeset lynq:05-create-user-resume-table
CREATE TABLE IF NOT EXISTS lynq_backend_db.user_resumes (
    id          VARCHAR(36) NOT NULL,
    resume      JSON,
    language    ENUM('EN', 'ES', 'FR', 'PR') NOT NULL,
    created_on  DATE        NOT NULL DEFAULT (CURRENT_DATE),
    user_id     VARCHAR(36) NOT NULL,
    CONSTRAINT pk_user_resumes PRIMARY KEY (id),
    CONSTRAINT fk_user_resumes_user FOREIGN KEY (user_id) REFERENCES lynq_backend_db.users (id)
);