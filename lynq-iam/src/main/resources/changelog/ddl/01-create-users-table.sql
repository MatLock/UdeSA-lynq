--liquibase formatted sql

--changeset lynq:01-create-users-table
CREATE TABLE lynq_iam_db.users (
    id       CHAR(36)     NOT NULL PRIMARY KEY,
    email    VARCHAR(100) NOT NULL,
    username VARCHAR(20)  NOT NULL,
    password      VARCHAR(60)  NOT NULL,
    creation_date TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT chk_users_email   CHECK (email REGEXP '^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$')
);