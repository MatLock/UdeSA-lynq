from __future__ import annotations

import logging

import httpx

from config import Settings, get_settings
from middleware.request_uuid import REQUEST_UUID_HEADER

log = logging.getLogger(__name__)

SKILL_ENHANCE_PATH = "/dmz/skill-enhance"
DEFAULT_WORK_TYPE = "REMOTE"


class SkillExtractionFailed(Exception):
    pass


class LynqMlClient:

    def __init__(self, settings: Settings | None = None) -> None:
        settings = settings or get_settings()
        self._url = settings.lynq_ml_url + SKILL_ENHANCE_PATH
        self._timeout = settings.lynq_ml_timeout_seconds
        self._system_user_id = settings.system_user_id

    async def extract_skills(
        self, job: dict, request_uuid: str, user_id: str
    ) -> list[str]:
        body = {
            "title": job.get("title", ""),
            "description": job.get("description", ""),
            "work_type": job.get("workType") or DEFAULT_WORK_TYPE,
        }
        headers = {
            REQUEST_UUID_HEADER: request_uuid,
            "user-id": self._system_user_id or user_id,
        }

        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.post(self._url, json=body, headers=headers)
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise SkillExtractionFailed(str(exc)) from exc

        return list((payload.get("data") or {}).get("skills") or [])


_client: LynqMlClient | None = None


def get_lynq_ml_client() -> LynqMlClient:
    global _client
    if _client is None:
        _client = LynqMlClient()
    return _client


def reset_lynq_ml_client() -> None:
    global _client
    _client = None
