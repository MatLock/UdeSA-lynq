--liquibase formatted sql

--changeset lynq:09-create-tag-frequency
CREATE TABLE IF NOT EXISTS lynq_analytics_db.tag_frequency (
    tag          VARCHAR(255) NOT NULL,
    df           INT          NOT NULL,
    weight       DOUBLE       NOT NULL,
    computed_on  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_tag_frequency PRIMARY KEY (tag)
);
