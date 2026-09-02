--liquibase formatted sql

--changeset lynq:14-create-user-similarity-tags-table
CREATE TABLE IF NOT EXISTS lynq_backend_db.user_similarity_tags (
    id       VARCHAR(36)  NOT NULL,
    user_id  VARCHAR(36)  NOT NULL,
    similarity_tag VARCHAR(255) NOT NULL,
    CONSTRAINT pk_user_similarity_tags PRIMARY KEY (id),
    CONSTRAINT fk_user_similarity_tags_user FOREIGN KEY (user_id) REFERENCES lynq_backend_db.users (id),
    CONSTRAINT uq_user_similarity_tags UNIQUE (user_id, similarity_tag)
);

--changeset lynq:14-create-job-post-similarity-tags-table
CREATE TABLE IF NOT EXISTS lynq_backend_db.job_post_similarity_tags (
    id      VARCHAR(36)  NOT NULL,
    job_id  VARCHAR(36)  NOT NULL,
    similarity_tag VARCHAR(255) NOT NULL,
    CONSTRAINT pk_job_post_similarity_tags PRIMARY KEY (id),
    CONSTRAINT fk_job_post_similarity_tags_job_post FOREIGN KEY (job_id) REFERENCES lynq_backend_db.job_posts (id),
    CONSTRAINT uq_job_post_similarity_tags UNIQUE (job_id, similarity_tag)
);
