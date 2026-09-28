--liquibase formatted sql

--changeset lynq:01-message-recommendations
ALTER TABLE message ADD COLUMN recommendations JSON;
