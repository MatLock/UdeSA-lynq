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
from db.session import get_session_factory

SALT_ENV = "EVAL_CORPUS_SALT"


def anonymize_user(user_id: str, salt: str) -> str:
    return hashlib.sha256(f"{salt}:{user_id}".encode("utf-8")).hexdigest()[:16]


def strip_identity(resume: dict) -> dict:
    return {key: value for key, value in resume.items() if key != "personal_info"}


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

                row = {
                    "conversation": conversation.short_id,
                    "candidate": anonymize_user(conversation.user_id, salt),
                    "language": conversation.language,
                    "job": conversation.job_snapshot,
                    "base_resume": strip_identity(conversation.base_resume),
                    "tailored_resume": strip_identity(current.resume),
                    "changes": current.changes,
                    "score_before": conversation.score_before,
                    "score_after": conversation.score_after,
                    "status": conversation.status,
                    "turn_count": conversation.turn_count,
                }
                output.write(json.dumps(row, ensure_ascii=False) + "\n")
                written += 1

    return written


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Export (job, base resume, tailored resume) triples as JSONL for the "
            "thesis evaluation corpus. Contact details are dropped and the "
            "candidate id is hashed: the corpus must never be a dump of real "
            "resumes with names and phone numbers."
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
