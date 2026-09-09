--liquibase formatted sql

--changeset lynq:19-drop-user-type
ALTER TABLE lynq_backend_db.users DROP COLUMN type;
