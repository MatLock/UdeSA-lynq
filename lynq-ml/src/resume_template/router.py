"""Routes for rendering a resume into a PDF and uploading it to S3."""

from __future__ import annotations

import logging

from fastapi import APIRouter, Header, HTTPException, status
from fastapi.concurrency import run_in_threadpool

from file_uploader.uploader import upload_to_presigned_url
from response import GlobalRestResponse

from .models import ResumeTemplateCreationRequest
from .renderer import render_resume_pdf

log = logging.getLogger(__name__)

router = APIRouter()

_PDF_CONTENT_TYPE = "application/pdf"

# The lynq-request-uuid is rendered by the log formatter (MDC-style), so it is
# not repeated here — only the request-specific identifiers the format omits.
_LOG_CONTEXT = "user_id=%s, template=%s"


@router.post(
    "/resume-template-creation",
    status_code=status.HTTP_201_CREATED,
    response_model=GlobalRestResponse,
)
async def create_resume_template(
    body: ResumeTemplateCreationRequest,
    lynq_request_uuid: str = Header(alias="lynq-request-uuid"),
    user_id: str = Header(alias="user-id"),
) -> GlobalRestResponse:
    """Render the resume into a styled PDF and upload it to the presigned URL.

    Returns 201 once the PDF has been generated and stored. The service never
    holds AWS credentials: the PDF is streamed to the caller-provided presigned
    PUT URL.
    """
    template = body.template
    log.info(
        "message= Started resume-template-creation, " + _LOG_CONTEXT,
        user_id,
        template.value,
    )

    # Rendering (WeasyPrint) and uploading (urllib) are blocking; keep them off
    # the event loop so concurrent requests are not stalled.
    try:
        pdf = await run_in_threadpool(
            render_resume_pdf, body.resume_content, template, body.profile_url
        )
    except Exception as exc:  # noqa: BLE001 - any render failure is a 500
        log.error(
            "message= Error when executing resume-template-creation, PDF render failed, "
            + _LOG_CONTEXT,
            user_id,
            template.value,
            exc_info=exc,
        )
        raise HTTPException(
            status_code=500, detail="Failed to render resume PDF"
        ) from exc

    try:
        await run_in_threadpool(
            upload_to_presigned_url, body.put_resume_url, pdf, _PDF_CONTENT_TYPE
        )
    except OSError as exc:  # urllib errors (URLError/HTTPError) subclass OSError
        log.error(
            "message= Error when executing resume-template-creation, PDF upload failed, "
            + _LOG_CONTEXT,
            user_id,
            template.value,
            exc_info=exc,
        )
        raise HTTPException(
            status_code=502, detail=f"Failed to upload resume PDF: {exc}"
        ) from exc

    log.info(
        "message= Finished resume-template-creation, " + _LOG_CONTEXT + ", bytes=%s",
        user_id,
        template.value,
        len(pdf),
    )
    return GlobalRestResponse()
