--liquibase formatted sql

--changeset lynq:14-create-category-daily-salary
CREATE TABLE IF NOT EXISTS lynq_analytics_db.category_daily_salary (
    snapshot_on  DATE         NOT NULL,
    category     VARCHAR(64)  NOT NULL,
    work_type    VARCHAR(32)  NOT NULL,
    currency     CHAR(3)      NOT NULL,
    n            INT          NOT NULL,
    median       DOUBLE,
    p25          DOUBLE,
    p75          DOUBLE,
    CONSTRAINT pk_category_daily_salary PRIMARY KEY (snapshot_on, category, work_type, currency)
);
