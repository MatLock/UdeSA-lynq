from __future__ import annotations

import os
import sys
import tempfile
import uuid
from datetime import datetime, timedelta
from decimal import Decimal

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), "src"))

from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from db.models import (
    Base,
    Conversation,
    ConversationStatus,
    Message,
    MessageRole,
    ResumeVersion,
    SpanKind,
    TraceSpan,
)

JOB = {
    "id": "job-1",
    "title": "Senior Backend Engineer",
    "company": "Acme",
    "description": "Backend with Kubernetes, PostgreSQL and Jenkins.",
    "workType": "REMOTE",
    "skills": ["Kubernetes", "PostgreSQL", "Jenkins"],
    "extractedSkills": ["Kubernetes", "PostgreSQL"],
}

RESUME = {
    "personal_info": {"full_name": "Ada Lovelace", "email": "ada@example.com"},
    "summary": "Backend engineer with eight years on distributed systems.",
    "skills": {"technical": ["Java", "Postgres"], "tools": ["Docker"]},
}


class TemporaryDatabase:

    def __init__(self) -> None:
        handle, self.path = tempfile.mkstemp(suffix=".sqlite")
        os.close(handle)
        self.engine = create_async_engine(f"sqlite+aiosqlite:///{self.path}")
        self.session_factory = async_sessionmaker(
            bind=self.engine, expire_on_commit=False
        )

    async def create_schema(self) -> None:
        async with self.engine.begin() as connection:
            await connection.run_sync(Base.metadata.create_all)

    async def dispose(self) -> None:
        await self.engine.dispose()
        os.unlink(self.path)


def new_conversation(age_days: int = 0, **overrides) -> Conversation:
    moment = datetime(2026, 9, 19, 12, 0, 0) - timedelta(days=age_days)
    values = dict(
        id=str(uuid.uuid4()),
        user_id="user-1",
        job_id=JOB["id"],
        base_resume_id="resume-1",
        job_snapshot=JOB,
        base_resume=RESUME,
        language="es",
        resume_language="en",
        status=ConversationStatus.ACTIVE,
        llm_provider="bedrock",
        llm_model="amazon.nova-pro-v1:0",
        input_price_per_1m=Decimal("0.8000"),
        output_price_per_1m=Decimal("3.2000"),
        max_turns=10,
        max_steps=12,
        turn_count=1,
        created_on=moment,
        updated_on=moment,
    )
    values.update(overrides)
    return Conversation(**values)


def new_message(conversation: Conversation, seq: int = 1, **overrides) -> Message:
    values = dict(
        id=str(uuid.uuid4()),
        conversation_id=conversation.id,
        seq=seq,
        role=MessageRole.USER,
        content="Go ahead",
        turn_key=str(uuid.uuid4()),
        created_on=conversation.created_on,
    )
    values.update(overrides)
    return Message(**values)


def new_resume_version(conversation: Conversation, version: int = 1, **overrides):
    values = dict(
        id=str(uuid.uuid4()),
        conversation_id=conversation.id,
        version=version,
        resume=RESUME,
        changes=[{"section": "summary", "kind": "rewrite", "detail": "tightened"}],
        produced_by=None,
        is_current=True,
        created_on=conversation.created_on,
    )
    values.update(overrides)
    return ResumeVersion(**values)


def new_trace_span(conversation: Conversation, **overrides) -> TraceSpan:
    values = dict(
        id=str(uuid.uuid4()),
        conversation_id=conversation.id,
        message_id=None,
        parent_id=None,
        step=1,
        kind=SpanKind.LLM,
        name="tailor",
        input="the whole prompt with the resume in it",
        output="the whole answer with the resume in it",
        prompt_tokens=1200,
        completion_tokens=300,
        cost_usd=Decimal("0.00204000"),
        latency_ms=1800,
        created_on=conversation.created_on,
    )
    values.update(overrides)
    return TraceSpan(**values)
