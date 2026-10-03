from __future__ import annotations

import logging
from typing import Annotated, Optional

from fastapi import APIRouter, BackgroundTasks, Body, Header, HTTPException, Response

from backend_client import BackendClient, BackendError
from config import get_settings
from logging_context import request_uuid_ctx
from model import VerifyOverrides
from router import run_control
from router.run_control import RunGuard
from service import VerifyService

log = logging.getLogger(__name__)

router = APIRouter()

verify_guard = RunGuard()


def build_verify_service() -> VerifyService:
    settings = get_settings()
    return VerifyService(
        settings=settings,
        backend_client=BackendClient(
            base_url=settings.backend_url,
            internal_token=settings.internal_token,
            timeout=settings.http_timeout,
        ),
    )


async def run_verify(
    service: VerifyService, request_uuid: str, overrides: Optional[VerifyOverrides]
) -> None:
    token = request_uuid_ctx.set(request_uuid)
    try:
        if run_control.feeder_runs.locked():
            log.info("message= Waiting for the feeder run in progress before verifying")
        async with run_control.feeder_runs:
            report = await service.run(request_uuid, overrides)
        log.info(
            "message= Finished feeder verify run, checked=%s, expired=%s",
            report.checked,
            report.expired,
        )
    except BackendError as exc:
        log.error(
            "message= Feeder verify run aborted, a lynq-app-backend call failed", exc_info=exc
        )
    except Exception as exc:  # NOSONAR
        log.error("message= Feeder verify run failed", exc_info=exc)
    finally:
        verify_guard.finish()
        request_uuid_ctx.reset(token)


@router.post(
    "/verify",
    status_code=202,
    response_class=Response,
    responses={
        202: {
            "description": "The run was accepted and started in the background, after any "
            "ingest run in progress. There is no body: the outcome is in the pod's logs.",
        },
        400: {"description": "The run was scoped to a source the service does not know."},
        409: {"description": "A verify run is already in progress; this one was not started."},
    },
)
async def verify(
    background_tasks: BackgroundTasks,
    lynq_request_uuid: Annotated[str, Header(alias="lynq-request-uuid")],
    overrides: Annotated[Optional[VerifyOverrides], Body()] = None,
) -> Response:
    service = build_verify_service()
    try:
        plan = service.validate(overrides)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    if not verify_guard.start():
        log.warning("message= Refused feeder verify run, another one is already in progress")
        raise HTTPException(
            status_code=409, detail="A feeder verify run is already in progress"
        )

    log.info(
        "message= Accepted feeder verify run, sources=%s, max_checks=%s",
        plan.sources,
        plan.max_checks,
    )
    background_tasks.add_task(run_verify, service, lynq_request_uuid, overrides)
    return Response(status_code=202)
