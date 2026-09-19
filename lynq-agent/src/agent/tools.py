from __future__ import annotations

import json
import logging

from langchain_core.tools import StructuredTool

from agent.context import STEP_LIMIT_MESSAGE, TurnContext
from agent.evidence import find_evidence
from client import LynqMlClient, MlError

log = logging.getLogger(__name__)

NO_EVIDENCE = (
    "NO_EVIDENCE: the base resume does not back this claim. Do not add it; "
    "tell the candidate you found no backing for it."
)


def build_tools(
    context: TurnContext,
    ml_client: LynqMlClient,
    request_uuid: str,
    on_limit=None,
) -> list[StructuredTool]:

    def _limit_hit() -> bool:
        if not context.limit_reached:
            return False
        if not context.limit_reported:
            context.limit_reported = True
            if on_limit is not None:
                on_limit(f"tool_steps={context.tool_steps}, max_steps={context.max_steps}")
        return True

    async def job_requirements() -> str:
        if _limit_hit():
            return STEP_LIMIT_MESSAGE
        context.count_step()

        if context.job_requirements is None:
            job = context.job_snapshot
            declared = [*(job.get("skills") or []), *(job.get("similarity_tags") or [])]
            try:
                extracted = await ml_client.job_skills(
                    request_uuid,
                    title=job.get("title") or "",
                    description=job.get("description") or "",
                    work_type=job.get("work_type") or "REMOTE",
                )
            except MlError as exc:
                log.warning(
                    "message= skill extraction failed, falling back to the job "
                    "snapshot skills, conversation_id=%s",
                    context.conversation_id,
                    exc_info=exc,
                )
                extracted = []

            merged: list[str] = []
            seen: set[str] = set()
            for name in [*declared, *extracted]:
                key = name.strip().lower()
                if key and key not in seen:
                    seen.add(key)
                    merged.append(name)
            context.job_requirements = merged

        return json.dumps(context.job_requirements, ensure_ascii=False)

    async def find_evidence_tool(claim: str) -> str:
        if _limit_hit():
            return STEP_LIMIT_MESSAGE
        context.count_step()

        paths = find_evidence(context.base_resume, claim)
        context.evidence_log.append({"claim": claim, "paths": paths})
        if not paths:
            return NO_EVIDENCE
        return json.dumps(paths, ensure_ascii=False)

    async def apply_edit(section: str, op: str, payload: dict | None = None) -> str:
        if _limit_hit():
            return STEP_LIMIT_MESSAGE
        context.count_step()

        result = context.editor.apply(section, op, payload)
        return result.message

    return [
        StructuredTool.from_function(
            coroutine=job_requirements,
            name="job_requirements",
            description=(
                "Returns the list of skills and capabilities the posting asks "
                "for. Call it once, at the start of the turn."
            ),
        ),
        StructuredTool.from_function(
            coroutine=find_evidence_tool,
            name="find_evidence",
            description=(
                "Looks in the base resume for backing of a claim (a technology "
                "name, for instance). Returns the JSON paths where it appears, or "
                "NO_EVIDENCE. Use it before adding any skill."
            ),
        ),
        StructuredTool.from_function(
            coroutine=apply_edit,
            name="apply_edit",
            description=(
                "Applies a change to the resume and returns OK, or REJECTED "
                "with the reason. Sections and operations: summary/rewrite {text}; "
                "skills/add {bucket,name}; skills/remove {bucket,name}; "
                "skills/reorder {bucket,order}; "
                "work_experience|education|projects|certifications|languages/reorder "
                "{order}; work_experience|education|projects/rewrite "
                "{index,description,achievements,technologies}. "
                "The resume is edited ONLY through this tool."
            ),
        ),
    ]
