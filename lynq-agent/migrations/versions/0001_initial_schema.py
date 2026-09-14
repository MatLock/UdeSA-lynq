from __future__ import annotations

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import mysql

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

MEDIUM_TEXT = sa.Text().with_variant(mysql.MEDIUMTEXT(), "mysql")
MILLIS_DATETIME = sa.DateTime().with_variant(mysql.DATETIME(fsp=3), "mysql")


def upgrade() -> None:
    op.create_table(
        "conversation",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("short_id", sa.Integer(), nullable=True),
        sa.Column("user_id", sa.String(64), nullable=False),
        sa.Column("job_id", sa.String(64), nullable=False),
        sa.Column("base_resume_id", sa.String(64), nullable=False),
        sa.Column("job_snapshot", sa.JSON(), nullable=False),
        sa.Column("base_resume", sa.JSON(), nullable=False),
        sa.Column("language", sa.String(8), nullable=False),
        sa.Column("status", sa.String(32), nullable=False),
        sa.Column("llm_provider", sa.String(32), nullable=False),
        sa.Column("llm_model", sa.String(128), nullable=False),
        sa.Column("input_price_per_1m", sa.Numeric(10, 4), nullable=False),
        sa.Column("output_price_per_1m", sa.Numeric(10, 4), nullable=False),
        sa.Column("max_turns", sa.Integer(), nullable=False),
        sa.Column("max_steps", sa.Integer(), nullable=False),
        sa.Column("turn_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("score_before", sa.Integer(), nullable=True),
        sa.Column("score_after", sa.Integer(), nullable=True),
        sa.Column("applied_resume_id", sa.String(64), nullable=True),
        sa.Column("llm_calls", sa.Integer(), nullable=False, server_default="0"),
        sa.Column(
            "total_prompt_tokens", sa.BigInteger(), nullable=False, server_default="0"
        ),
        sa.Column(
            "total_completion_tokens",
            sa.BigInteger(),
            nullable=False,
            server_default="0",
        ),
        sa.Column(
            "cost_usd", sa.Numeric(16, 8), nullable=False, server_default="0"
        ),
        sa.Column("job_requirements", sa.JSON(), nullable=True),
        sa.Column("created_on", sa.DateTime(), nullable=False),
        sa.Column("updated_on", sa.DateTime(), nullable=False),
        sa.UniqueConstraint("short_id", name="uk_short_id"),
    )
    op.create_index("idx_user", "conversation", ["user_id", "created_on"])

    if op.get_bind().dialect.name == "mysql":
        op.execute(
            "ALTER TABLE conversation MODIFY short_id INT NOT NULL AUTO_INCREMENT"
        )

    op.create_table(
        "message",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("conversation_id", sa.String(36), nullable=False),
        sa.Column("seq", sa.Integer(), nullable=False),
        sa.Column("role", sa.String(16), nullable=False),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("turn_key", sa.String(36), nullable=True),
        sa.Column("warnings", sa.JSON(), nullable=True),
        sa.Column("created_on", sa.DateTime(), nullable=False),
        sa.ForeignKeyConstraint(["conversation_id"], ["conversation.id"]),
        sa.UniqueConstraint("conversation_id", "turn_key", name="uk_turn"),
        sa.UniqueConstraint("conversation_id", "seq", name="uk_seq"),
    )

    op.create_table(
        "resume_version",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("conversation_id", sa.String(36), nullable=False),
        sa.Column("version", sa.Integer(), nullable=False),
        sa.Column("resume", sa.JSON(), nullable=False),
        sa.Column("changes", sa.JSON(), nullable=False),
        sa.Column("produced_by", sa.String(36), nullable=True),
        sa.Column("is_current", sa.Boolean(), nullable=False),
        sa.Column("created_on", sa.DateTime(), nullable=False),
        sa.ForeignKeyConstraint(["conversation_id"], ["conversation.id"]),
        sa.UniqueConstraint("conversation_id", "version", name="uk_version"),
    )

    op.create_table(
        "trace_span",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("conversation_id", sa.String(36), nullable=False),
        sa.Column("message_id", sa.String(36), nullable=True),
        sa.Column("parent_id", sa.String(36), nullable=True),
        sa.Column("step", sa.Integer(), nullable=False),
        sa.Column("kind", sa.String(24), nullable=False),
        sa.Column("name", sa.String(64), nullable=False),
        sa.Column("input", MEDIUM_TEXT, nullable=True),
        sa.Column("output", MEDIUM_TEXT, nullable=True),
        sa.Column("prompt_tokens", sa.Integer(), nullable=True),
        sa.Column("completion_tokens", sa.Integer(), nullable=True),
        sa.Column("cached_prompt_tokens", sa.Integer(), nullable=True),
        sa.Column("cost_usd", sa.Numeric(16, 8), nullable=True),
        sa.Column("latency_ms", sa.Integer(), nullable=True),
        sa.Column("error", sa.Text(), nullable=True),
        sa.Column("created_on", MILLIS_DATETIME, nullable=False),
    )
    op.create_index("idx_conv", "trace_span", ["conversation_id", "created_on"])


def downgrade() -> None:
    op.drop_index("idx_conv", table_name="trace_span")
    op.drop_table("trace_span")
    op.drop_table("resume_version")
    op.drop_table("message")
    op.drop_index("idx_user", table_name="conversation")
    op.drop_table("conversation")
