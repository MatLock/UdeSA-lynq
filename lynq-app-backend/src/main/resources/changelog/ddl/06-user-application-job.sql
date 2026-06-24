--liquibase formatted sql

--changeset lynq:06-create-user-application-job-table
CREATE TABLE IF NOT EXISTS lynq_backend_db.user_application_job (
    id                   VARCHAR(36) NOT NULL,
    job_post_id               VARCHAR(36) NOT NULL,
    user_id              VARCHAR(36) NOT NULL,
    applied_on           DATE        NOT NULL DEFAULT (CURRENT_DATE),
    application_seen_on  DATE,
    CONSTRAINT pk_user_application_job PRIMARY KEY (id),
    CONSTRAINT fk_user_application_job_post FOREIGN KEY (job_post_id) REFERENCES lynq_backend_db.job_posts (id),
    CONSTRAINT fk_user_application_job_user FOREIGN KEY (user_id) REFERENCES lynq_backend_db.users (id),
    CONSTRAINT uq_user_application_job UNIQUE (job_post_id, user_id)
);