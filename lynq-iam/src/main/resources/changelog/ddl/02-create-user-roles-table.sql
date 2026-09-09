--liquibase formatted sql

--changeset lynq:02-create-user-roles-table
CREATE TABLE lynq_iam_db.user_roles (
    user_id CHAR(36)    NOT NULL,
    role    VARCHAR(32) NOT NULL,

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES lynq_iam_db.users (id)
);
