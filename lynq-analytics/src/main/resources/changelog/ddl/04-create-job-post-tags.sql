--liquibase formatted sql

--changeset lynq:04-create-job-post-tags
CREATE TABLE IF NOT EXISTS lynq_analytics_db.job_post_tags (
    job_id  VARCHAR(36)  NOT NULL,
    tag     VARCHAR(255) NOT NULL,
    CONSTRAINT pk_job_post_tags PRIMARY KEY (job_id, tag),
    CONSTRAINT fk_job_post_tags_job_post FOREIGN KEY (job_id) REFERENCES lynq_analytics_db.job_posts (id),
    INDEX idx_job_post_tags_tag (tag)
);
