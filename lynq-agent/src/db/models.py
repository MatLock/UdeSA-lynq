from __future__ import annotations

from datetime import datetime
from decimal import Decimal

from sqlalchemy import (
    JSON,
    BigInteger,
    Boolean,
    DateTime,
    FetchedValue,
    ForeignKey,
    Index,
    Integer,
    Numeric,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.dialects import mysql
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

MEDIUM_TEXT = Text().with_variant(mysql.MEDIUMTEXT(), "mysql")
MILLIS_DATETIME = DateTime().with_variant(mysql.DATETIME(fsp=3), "mysql")


class Base(DeclarativeBase):
    pass


class ConversationStatus:
    AWAITING_CONFIRMATION = "AWAITING_CONFIRMATION"
    ACTIVE = "ACTIVE"
    RUNNING = "RUNNING"
    APPLIED = "APPLIED"
    EXHAUSTED = "EXHAUSTED"
    ABANDONED = "ABANDONED"

    OPEN = (AWAITING_CONFIRMATION, ACTIVE)
    CLOSED = (APPLIED, EXHAUSTED, ABANDONED)


class MessageRole:
    USER = "user"
    ASSISTANT = "assistant"
    SYSTEM = "system"


class SpanKind:
    LLM = "llm"
    TOOL = "tool"
    THOUGHT = "thought"
    LIMIT = "limit"
    ERROR = "error"


class Conversation(Base):
    __tablename__ = "conversation"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    short_id: Mapped[int | None] = mapped_column(
        Integer, server_default=FetchedValue(), unique=True, nullable=True
    )
    user_id: Mapped[str] = mapped_column(String(64), nullable=False)
    job_id: Mapped[str] = mapped_column(String(64), nullable=False)
    base_resume_id: Mapped[str] = mapped_column(String(64), nullable=False)
    job_snapshot: Mapped[dict] = mapped_column(JSON, nullable=False)
    base_resume: Mapped[dict] = mapped_column(JSON, nullable=False)
    language: Mapped[str] = mapped_column(String(8), nullable=False)
    resume_language: Mapped[str] = mapped_column(String(8), nullable=False)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    run_token: Mapped[str | None] = mapped_column(String(36), nullable=True)
    llm_provider: Mapped[str] = mapped_column(String(32), nullable=False)
    llm_model: Mapped[str] = mapped_column(String(128), nullable=False)

    input_price_per_1m: Mapped[Decimal] = mapped_column(Numeric(10, 4), nullable=False)
    output_price_per_1m: Mapped[Decimal] = mapped_column(Numeric(10, 4), nullable=False)

    max_turns: Mapped[int] = mapped_column(Integer, nullable=False)
    max_steps: Mapped[int] = mapped_column(Integer, nullable=False)
    turn_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    applied_resume_id: Mapped[str | None] = mapped_column(String(64), nullable=True)

    llm_calls: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    total_prompt_tokens: Mapped[int] = mapped_column(
        BigInteger, nullable=False, default=0
    )
    total_completion_tokens: Mapped[int] = mapped_column(
        BigInteger, nullable=False, default=0
    )
    cost_usd: Mapped[Decimal] = mapped_column(
        Numeric(16, 8), nullable=False, default=Decimal("0")
    )

    created_on: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    updated_on: Mapped[datetime] = mapped_column(DateTime, nullable=False)

    __table_args__ = (
        Index("idx_user", "user_id", "created_on"),
        Index("idx_status_updated", "status", "updated_on"),
        Index("idx_created", "created_on"),
    )


class Message(Base):
    __tablename__ = "message"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    conversation_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("conversation.id"), nullable=False
    )
    seq: Mapped[int] = mapped_column(Integer, nullable=False)
    role: Mapped[str] = mapped_column(String(16), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    turn_key: Mapped[str | None] = mapped_column(String(36), nullable=True)
    warnings: Mapped[list | None] = mapped_column(JSON, nullable=True)
    created_on: Mapped[datetime] = mapped_column(DateTime, nullable=False)

    __table_args__ = (
        UniqueConstraint("conversation_id", "turn_key", name="uk_turn"),
        UniqueConstraint("conversation_id", "seq", name="uk_seq"),
    )


class ResumeVersion(Base):
    __tablename__ = "resume_version"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    conversation_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("conversation.id"), nullable=False
    )
    version: Mapped[int] = mapped_column(Integer, nullable=False)
    resume: Mapped[dict] = mapped_column(JSON, nullable=False)
    changes: Mapped[list] = mapped_column(JSON, nullable=False)
    produced_by: Mapped[str | None] = mapped_column(String(36), nullable=True)
    is_current: Mapped[bool] = mapped_column(Boolean, nullable=False)
    created_on: Mapped[datetime] = mapped_column(DateTime, nullable=False)

    __table_args__ = (
        UniqueConstraint("conversation_id", "version", name="uk_version"),
    )


class TraceSpan(Base):
    __tablename__ = "trace_span"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    conversation_id: Mapped[str] = mapped_column(String(36), nullable=False)
    message_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    parent_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    step: Mapped[int] = mapped_column(Integer, nullable=False)
    kind: Mapped[str] = mapped_column(String(24), nullable=False)
    name: Mapped[str] = mapped_column(String(64), nullable=False)
    input: Mapped[str | None] = mapped_column(MEDIUM_TEXT, nullable=True)
    output: Mapped[str | None] = mapped_column(MEDIUM_TEXT, nullable=True)
    prompt_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    completion_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    cached_prompt_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    cost_usd: Mapped[Decimal | None] = mapped_column(Numeric(16, 8), nullable=True)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    error: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_on: Mapped[datetime] = mapped_column(MILLIS_DATETIME, nullable=False)

    __table_args__ = (Index("idx_conv", "conversation_id", "created_on"),)
