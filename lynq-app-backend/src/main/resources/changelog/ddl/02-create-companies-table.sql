--liquibase formatted sql

--changeset lynq:02-create-company-table
CREATE TABLE IF NOT EXISTS lynq_backend_db.companies (
    id                 VARCHAR(36)  NOT NULL,
    name               VARCHAR(255) NOT NULL,
    about              TEXT,
    size               INT,
    lynq_file_storage_id VARCHAR(36),
    created_on         DATE         NOT NULL DEFAULT (CURRENT_DATE),
    CONSTRAINT pk_companies PRIMARY KEY (id),
    CONSTRAINT uq_companies_name UNIQUE (name)
);

