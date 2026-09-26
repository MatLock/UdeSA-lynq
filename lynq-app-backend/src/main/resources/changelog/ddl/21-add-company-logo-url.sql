--liquibase formatted sql

--changeset lynq:21-add-company-logo-url
ALTER TABLE lynq_backend_db.companies
    ADD COLUMN logo_url VARCHAR(2048) NULL AFTER lynq_file_storage_id;
