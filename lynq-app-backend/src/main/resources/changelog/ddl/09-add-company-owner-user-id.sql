--liquibase formatted sql

--changeset lynq:09-add-company-owner-user-id
ALTER TABLE lynq_backend_db.companies
    ADD COLUMN owner_user_id VARCHAR(36) AFTER created_on,
    ADD CONSTRAINT fk_companies_owner_user FOREIGN KEY (owner_user_id) REFERENCES lynq_backend_db.users (id);