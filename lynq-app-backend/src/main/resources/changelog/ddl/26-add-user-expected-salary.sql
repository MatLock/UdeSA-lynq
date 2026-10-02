--liquibase formatted sql

--changeset lynq:26-add-user-expected-salary
ALTER TABLE lynq_backend_db.users
    ADD COLUMN expected_salary INT NULL AFTER birth_date,
    ADD COLUMN expected_salary_currency CHAR(3) NULL AFTER expected_salary;
