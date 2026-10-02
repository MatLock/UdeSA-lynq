--liquibase formatted sql

--changeset lynq:24-add-job-post-salary-currency-and-category
ALTER TABLE lynq_backend_db.job_posts
    ADD COLUMN salary_currency CHAR(3) NULL AFTER salary_range_top,
    ADD COLUMN category VARCHAR(64) NULL AFTER salary_currency;

UPDATE lynq_backend_db.job_posts
SET salary_currency = 'ARS'
WHERE salary_range_down IS NOT NULL
   OR salary_range_top IS NOT NULL;
