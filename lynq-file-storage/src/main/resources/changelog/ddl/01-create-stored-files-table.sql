--liquibase formatted sql

--changeset lynq:01-create-stored-files-table
CREATE TABLE IF NOT EXISTS lynq_file_storage_db.stored_files (
    id            VARCHAR(36)   NOT NULL,
    file_name     VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(255),
    s3_key        VARCHAR(1024) NOT NULL,
    status        ENUM('PENDING', 'AVAILABLE') NOT NULL,
    created_on    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stored_files PRIMARY KEY (id)
);
