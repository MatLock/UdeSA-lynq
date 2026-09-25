from __future__ import annotations

import os
import sys
import tempfile
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), "src"))

from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult

from agent.context import SpanRecord, TurnOutcome
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


STUB_REPLY = "I moved Kubernetes to the front of your experience."


async def stub_loop(context) -> TurnOutcome:
    context.spans.append(
        SpanRecord(
            step=1,
            kind=SpanKind.LLM,
            name="model",
            input='{"messages": []}',
            output=STUB_REPLY,
            prompt_tokens=1200,
            completion_tokens=180,
            latency_ms=12,
        )
    )
    context.spans.append(
        SpanRecord(
            step=2,
            kind=SpanKind.TOOL,
            name="apply_edit",
            input='{"section": "summary"}',
            output="OK",
            latency_ms=1,
        )
    )
    return TurnOutcome(
        reply=STUB_REPLY,
        resume=context.current_resume,
        changes=[
            {"section": "summary", "kind": "rewrite", "detail": "rewrote the summary"}
        ],
        warnings=[],
        spans=context.spans,
    )


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
    moment = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(days=age_days)
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


class ScriptedChatModel(BaseChatModel):

    answers: list = []
    binds: list = []
    prompts: list = []

    @property
    def _llm_type(self) -> str:
        return "scripted"

    def bind_tools(self, tools, **kwargs):
        self.binds.append(
            {
                "tools": [getattr(tool, "name", str(tool)) for tool in tools],
                **kwargs,
            }
        )
        return self.bind()

    def _generate(self, messages, stop=None, run_manager=None, **kwargs) -> ChatResult:
        self.prompts.append(messages)
        answer = self.answers[min(len(self.prompts) - 1, len(self.answers) - 1)]
        return ChatResult(generations=[ChatGeneration(message=answer)])


def scripted(*answers: AIMessage) -> ScriptedChatModel:
    return ScriptedChatModel(answers=list(answers), binds=[], prompts=[])


def tool_call(name: str, arguments: dict, call_id: str = "call-1") -> AIMessage:
    return AIMessage(
        content="",
        tool_calls=[{"name": name, "args": arguments, "id": call_id}],
        usage_metadata={"input_tokens": 1200, "output_tokens": 40, "total_tokens": 1240},
    )
