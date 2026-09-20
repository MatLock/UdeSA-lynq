from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), "src"))

from sqlalchemy import select

from db.models import Conversation, ResumeVersion
from db.session import dispose_engine, get_session_factory

SALT_ENV = "EVAL_CORPUS_SALT"
IDENTITY_FIELD = "personal_info"


def anonymize_user(user_id: str, salt: str) -> str:
    return hashlib.sha256(f"{salt}:{user_id}".encode("utf-8")).hexdigest()[:16]


def strip_identity(resume: dict) -> dict:
    return {key: value for key, value in resume.items() if key != IDENTITY_FIELD}


def build_row(conversation: Conversation, current: ResumeVersion, salt: str) -> dict:
    return {
        "conversation": conversation.short_id,
        "candidate": anonymize_user(conversation.user_id, salt),
        "language": conversation.language,
        "resume_language": conversation.resume_language,
        "job": conversation.job_snapshot,
        "base_resume": strip_identity(conversation.base_resume),
        "tailored_resume": strip_identity(current.resume),
        "changes": current.changes,
        "status": conversation.status,
        "turn_count": conversation.turn_count,
    }


async def export(output_path: str, salt: str, only_applied: bool) -> int:
    session_factory = get_session_factory()
    written = 0

    async with session_factory() as session:
        query = select(Conversation).order_by(Conversation.created_on)
        if only_applied:
            query = query.where(Conversation.status == "APPLIED")
        conversations = (await session.scalars(query)).all()

        with open(output_path, "w", encoding="utf-8") as output:
            for conversation in conversations:
                current = await session.scalar(
                    select(ResumeVersion).where(
                        ResumeVersion.conversation_id == conversation.id,
                        ResumeVersion.is_current.is_(True),
                    )
                )
                if current is None or current.version == 0:
                    continue

                row = build_row(conversation, current, salt)
                output.write(json.dumps(row, ensure_ascii=False) + "\n")
                written += 1

    await dispose_engine()
    return written


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Export (job posting, base resume, tailored resume) triples as JSONL "
            "for the thesis evaluation corpus. Contact details are dropped and "
            "the candidate id is hashed: the corpus is never a dump of real "
            "resumes with names and phone numbers. Run it before "
            "AGENT_CONVERSATION_TTL_DAYS wipes the conversations."
        )
    )
    parser.add_argument("output", help="destination .jsonl file")
    parser.add_argument(
        "--only-applied",
        action="store_true",
        help="keep only conversations that ended in an application",
    )
    arguments = parser.parse_args()

    salt = os.getenv(SALT_ENV)
    if not salt:
        parser.error(
            f"{SALT_ENV} is required: without a salt the candidate hashes are "
            "reversible by brute force over the user id space"
        )

    written = asyncio.run(export(arguments.output, salt, arguments.only_applied))
    print(f"{written} triples written to {arguments.output}")


if __name__ == "__main__":
    main()
