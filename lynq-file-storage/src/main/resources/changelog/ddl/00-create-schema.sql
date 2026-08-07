--liquibase formatted sql

--changeset lynq:00-create-schema
CREATE SCHEMA IF NOT EXISTS lynq_file_storage_db;
