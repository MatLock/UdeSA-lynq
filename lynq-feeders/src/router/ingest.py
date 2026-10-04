from __future__ import annotations

import logging
from typing import Annotated, Optional

from fastapi import APIRouter, BackgroundTasks, Body, Header, HTTPException, Response

from backend_client import BackendClient, BackendError
from config import get_settings
from logging_context import request_uuid_ctx
from llm_client import LlmClient
from model import IngestOverrides
from router import run_control
from router.run_control import RunGuard
from service import EnrichmentError, IngestService

log = logging.getLogger(__name__)

router = APIRouter()

run_guard = RunGuard()


def build_service() -> IngestService:
    settings = get_settings()
    return IngestService(
        settings=settings,
        llm_client=LlmClient(
            base_url=settings.llm_url,
            system_user_id=settings.system_user_id,
            internal_token=settings.internal_token,
            timeout=settings.llm_timeout,
        ),
        backend_client=BackendClient(
            base_url=settings.backend_url,
            internal_token=settings.internal_token,
            timeout=settings.http_timeout,
        ),
    )


async def run_ingest(
    service: IngestService, request_uuid: str, overrides: Optional[IngestOverrides]
) -> None:
    token = request_uuid_ctx.set(request_uuid)
    try:
        async with run_control.feeder_runs:
            report = await service.run(request_uuid, overrides)
        log.info(
            "message= Finished feeder ingest run, ingested_jobs=%s, reopened_jobs=%s",
            report.ingested.jobs,
            report.ingested.reopened,
        )
    except EnrichmentError as exc:
        log.error("message= Feeder ingest run aborted, skill extraction failed", exc_info=exc)
    except BackendError as exc:
        log.error("message= Feeder ingest run aborted, the job post ingest failed", exc_info=exc)
    except Exception as exc:  # NOSONAR
        log.error("message= Feeder ingest run failed", exc_info=exc)
    finally:
        run_guard.finish()
        request_uuid_ctx.reset(token)


@router.post(
    "/ingest",
    status_code=202,
    response_class=Response,
    responses={
        202: {
            "description": "The run was accepted and started in the background. "
            "There is no body: the outcome is in the pod's logs.",
        },
        400: {"description": "The run was scoped to a source the service does not know."},
        409: {"description": "A run is already in progress; this one was not started."},
    },
)
async def ingest(
    background_tasks: BackgroundTasks,
    lynq_request_uuid: Annotated[str, Header(alias="lynq-request-uuid")],
    overrides: Annotated[Optional[IngestOverrides], Body()] = None,
) -> Response:
    service = build_service()
    try:
        plan = service.validate(overrides)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    if not run_guard.start():
        log.warning("message= Refused feeder ingest run, another one is already in progress")
        raise HTTPException(
            status_code=409, detail="A feeder ingest run is already in progress"
        )

    log.info(
        "message= Accepted feeder ingest run, sources=%s, categories=%s, jobs_per_category=%s",
        plan.sources,
        plan.categories,
        plan.jobs_per_category,
    )
    background_tasks.add_task(run_ingest, service, lynq_request_uuid, overrides)
    return Response(status_code=202)
