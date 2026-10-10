--liquibase formatted sql

--changeset lynq:02-conversation-confirmed
ALTER TABLE conversation ADD COLUMN confirmed JSON;
