--liquibase formatted sql

--changeset lynq:16-create-supported-languages-table
CREATE TABLE IF NOT EXISTS lynq_backend_db.supported_languages (
    code  VARCHAR(5)   NOT NULL,
    name  VARCHAR(64)  NOT NULL,
    CONSTRAINT pk_supported_languages PRIMARY KEY (code)
);

--changeset lynq:16-seed-supported-languages
INSERT INTO lynq_backend_db.supported_languages (code, name) VALUES
    ('EN', 'English'),
    ('ES', 'Español'),
    ('FR', 'Français'),
    ('PR', 'Português');
