from __future__ import annotations

import logging
from typing import Annotated, Optional

from fastapi import APIRouter, Body, Header, HTTPException

from backend_client import BackendClient, BackendError
from config import get_settings
from ml_client import MlClient
from model import IngestOverrides
from response import GlobalRestResponse
from service import IngestReport, IngestService

log = logging.getLogger(__name__)

router = APIRouter()


def build_service() -> IngestService:
    settings = get_settings()
    return IngestService(
        settings=settings,
        ml_client=MlClient(
            base_url=settings.ml_url,
            system_user_id=settings.system_user_id,
            timeout=settings.ml_timeout,
        ),
        backend_client=BackendClient(
            base_url=settings.backend_url,
            internal_token=settings.internal_token,
            timeout=settings.http_timeout,
        ),
    )


@router.post(
    "/ingest",
    responses={
        400: {"description": "The run was scoped to a source the service does not know."},
        502: {"description": "The downstream job-post ingest failed."},
    },
)
async def ingest(
    lynq_request_uuid: Annotated[str, Header(alias="lynq-request-uuid")],
    overrides: Annotated[Optional[IngestOverrides], Body()] = None,
) -> GlobalRestResponse[IngestReport]:
    log.info("message= Started feeder ingest run")

    service = build_service()
    try:
        report = await service.run(lynq_request_uuid, overrides)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except BackendError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    log.info("message= Finished feeder ingest run, ingested_jobs=%s", report.ingested.jobs)
    return GlobalRestResponse(data=report)
