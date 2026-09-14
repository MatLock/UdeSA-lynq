from __future__ import annotations

import json
import logging

from langchain_core.tools import StructuredTool

from agent.context import STEP_LIMIT_MESSAGE, TurnContext
from agent.evidence import find_evidence
from client import LynqMlClient, MlError

log = logging.getLogger(__name__)

NO_EVIDENCE = (
    "SIN EVIDENCIA: el CV base no respalda esta afirmacion. No la agregues; "
    "decile al candidato que no encontraste respaldo."
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
                "Devuelve la lista de skills y capacidades que pide el aviso. "
                "Llamala una sola vez, al principio del turno."
            ),
        ),
        StructuredTool.from_function(
            coroutine=find_evidence_tool,
            name="find_evidence",
            description=(
                "Busca en el CV base respaldo para una afirmacion (por ejemplo el "
                "nombre de una tecnologia). Devuelve las rutas JSON donde aparece, "
                "o SIN EVIDENCIA. Usala antes de agregar cualquier skill."
            ),
        ),
        StructuredTool.from_function(
            coroutine=apply_edit,
            name="apply_edit",
            description=(
                "Aplica un cambio al CV y devuelve OK o RECHAZADO con el motivo. "
                "Secciones y operaciones: summary/rewrite {text}; "
                "skills/add {bucket,name}; skills/remove {bucket,name}; "
                "skills/reorder {bucket,order}; "
                "work_experience|education|projects|certifications|languages/reorder "
                "{order}; work_experience|education|projects/rewrite "
                "{index,description,achievements,technologies}. "
                "El CV se edita SOLO con esta tool."
            ),
        ),
    ]
