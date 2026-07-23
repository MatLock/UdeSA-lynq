"""Route for the service liveness/readiness probe."""

from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from llm_client import LLMProvider, get_llm_client

router = APIRouter()


@router.get("/health")
async def health() -> JSONResponse:
    """Report service status plus the configured LLM's reachability.

    Returns ``200`` when the LLM is reachable, ``503`` otherwise. This route is
    intentionally *not* wrapped in ``GlobalRestResponse`` — it is an infra probe.
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
    body = {"status": "UP" if llm_up else "DOWN", "llm": llm}
    return JSONResponse(status_code=200 if llm_up else 503, content=body)
