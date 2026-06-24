--liquibase formatted sql

--changeset lynq:03-create-job-posts-table
CREATE TABLE IF NOT EXISTS lynq_backend_db.job_posts (
    id                  VARCHAR(36)  NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    work_type           ENUM('REMOTE', 'IN_OFFICE') NOT NULL,
    salary_range_lower  INT,
    salary_range_top    INT,
    job_url             VARCHAR(2048),
    job_post_type       ENUM('LYNQ', 'LINKEDIN', 'COMPUTRABAJO', 'BUMERAN') NOT NULL,
    created_by_user_id  VARCHAR(36),
    company_id          VARCHAR(36),
    created_on          DATE         NOT NULL DEFAULT (CURRENT_DATE),
    CONSTRAINT pk_job_posts PRIMARY KEY (id),
    CONSTRAINT fk_job_posts_created_by_user FOREIGN KEY (created_by_user_id) REFERENCES lynq_backend_db.users (id),
    CONSTRAINT fk_job_posts_company FOREIGN KEY (company_id) REFERENCES lynq_backend_db.companies (id)
);