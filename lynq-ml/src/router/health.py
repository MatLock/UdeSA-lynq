"""Route for the service liveness/readiness probe."""

from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from llm_client import LLMProvider, get_llm_client

from renderer.resume_template import pdf_renderer_available

router = APIRouter()


@router.get("/health")
async def health() -> JSONResponse:
    """Report service status plus the configured LLM's reachability.

    Returns ``200`` when the LLM is reachable, ``503`` otherwise. This route is
    intentionally *not* wrapped in ``GlobalRestResponse`` — it is an infra probe.

    ``renderer`` reports whether WeasyPrint found its native libraries, which
    only ``/resume-template-creation`` needs. It is deliberately not part of the
    status code: taking the pod out of rotation would break the seven endpoints
    that render nothing, so a missing Pango is surfaced, not fatal.
    """
    try:
        client = get_llm_client()
        provider = client.provider
        llm_up = await client.health_check()
    except ValueError:
        provider = None
        llm_up = False

    llm = {
        "provider": provider.value if isinstance(provider, LLMProvider) else None,
        "status": "UP" if llm_up else "DOWN",
    }
    renderer = {"status": "UP" if pdf_renderer_available() else "DOWN"}
    body = {"status": "UP" if llm_up else "DOWN", "llm": llm, "renderer": renderer}
    return JSONResponse(status_code=200 if llm_up else 503, content=body)
