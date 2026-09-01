--liquibase formatted sql

--changeset lynq:02-add-stored-file-owner
ALTER TABLE lynq_file_storage_db.stored_files
    ADD COLUMN owner_user_id VARCHAR(36) NULL;

CREATE INDEX idx_stored_files_owner_user_id
    ON lynq_file_storage_db.stored_files (owner_user_id);
