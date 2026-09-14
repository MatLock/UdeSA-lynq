--liquibase formatted sql

--changeset lynq:00-create-conversation-table
CREATE TABLE IF NOT EXISTS conversation (
    id                      VARCHAR(36)    NOT NULL,
    short_id                INT            NOT NULL AUTO_INCREMENT,
    user_id                 VARCHAR(64)    NOT NULL,
    job_id                  VARCHAR(64)    NOT NULL,
    base_resume_id          VARCHAR(64)    NOT NULL,
    job_snapshot            JSON           NOT NULL,
    base_resume             JSON           NOT NULL,
    language                VARCHAR(8)     NOT NULL,
    status                  VARCHAR(32)    NOT NULL,
    llm_provider            VARCHAR(32)    NOT NULL,
    llm_model               VARCHAR(128)   NOT NULL,
    input_price_per_1m      DECIMAL(10, 4) NOT NULL,
    output_price_per_1m     DECIMAL(10, 4) NOT NULL,
    max_turns               INT            NOT NULL,
    max_steps               INT            NOT NULL,
    turn_count              INT            NOT NULL DEFAULT 0,
    score_before            INT,
    score_after             INT,
    applied_resume_id       VARCHAR(64),
    llm_calls               INT            NOT NULL DEFAULT 0,
    total_prompt_tokens     BIGINT         NOT NULL DEFAULT 0,
    total_completion_tokens BIGINT         NOT NULL DEFAULT 0,
    cost_usd                DECIMAL(16, 8) NOT NULL DEFAULT 0,
    job_requirements        JSON,
    created_on              DATETIME       NOT NULL,
    updated_on              DATETIME       NOT NULL,
    CONSTRAINT pk_conversation PRIMARY KEY (id),
    CONSTRAINT uk_short_id UNIQUE (short_id),
    INDEX idx_user (user_id, created_on)
);

--changeset lynq:00-create-message-table
CREATE TABLE IF NOT EXISTS message (
    id              VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    seq             INT         NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         TEXT        NOT NULL,
    turn_key        VARCHAR(36),
    warnings        JSON,
    created_on      DATETIME    NOT NULL,
    CONSTRAINT pk_message PRIMARY KEY (id),
    CONSTRAINT uk_turn UNIQUE (conversation_id, turn_key),
    CONSTRAINT uk_seq UNIQUE (conversation_id, seq),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
);

--changeset lynq:00-create-resume-version-table
CREATE TABLE IF NOT EXISTS resume_version (
    id              VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    version         INT         NOT NULL,
    resume          JSON        NOT NULL,
    changes         JSON        NOT NULL,
    produced_by     VARCHAR(36),
    is_current      BOOLEAN     NOT NULL,
    created_on      DATETIME    NOT NULL,
    CONSTRAINT pk_resume_version PRIMARY KEY (id),
    CONSTRAINT uk_version UNIQUE (conversation_id, version),
    CONSTRAINT fk_resume_version_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
);

--changeset lynq:00-create-trace-span-table
CREATE TABLE IF NOT EXISTS trace_span (
    id                   VARCHAR(36)    NOT NULL,
    conversation_id      VARCHAR(36)    NOT NULL,
    message_id           VARCHAR(36),
    parent_id            VARCHAR(36),
    step                 INT            NOT NULL,
    kind                 VARCHAR(24)    NOT NULL,
    name                 VARCHAR(64)    NOT NULL,
    input                MEDIUMTEXT,
    output               MEDIUMTEXT,
    prompt_tokens        INT,
    completion_tokens    INT,
    cached_prompt_tokens INT,
    cost_usd             DECIMAL(16, 8),
    latency_ms           INT,
    error                TEXT,
    created_on           DATETIME(3)    NOT NULL,
    CONSTRAINT pk_trace_span PRIMARY KEY (id),
    INDEX idx_conv (conversation_id, created_on)
);
