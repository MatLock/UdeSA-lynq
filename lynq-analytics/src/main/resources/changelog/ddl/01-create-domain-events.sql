--liquibase formatted sql

--changeset lynq:01-create-domain-events
CREATE TABLE IF NOT EXISTS lynq_analytics_db.domain_events (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    event_id       CHAR(36)     NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    aggregate_type VARCHAR(32)  NOT NULL,
    aggregate_id   VARCHAR(36)  NOT NULL,
    payload        JSON         NOT NULL,
    occurred_on    DATETIME(6)  NOT NULL,
    received_on    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_domain_events PRIMARY KEY (id),
    CONSTRAINT uk_domain_events_event_id UNIQUE (event_id)
);
