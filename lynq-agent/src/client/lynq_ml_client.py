from __future__ import annotations

import logging

import httpx

log = logging.getLogger(__name__)

SKILL_EXTRACTION_PATH = "/dmz/skill-enhance"


class MlError(RuntimeError):
    pass


def _string_list(payload: dict, key: str) -> list[str]:
    value = payload.get(key)
    if isinstance(value, list) and all(isinstance(item, str) for item in value):
        return value
    return []


class LynqMlClient:

    def __init__(self, base_url: str, system_user_id: str, timeout: float) -> None:
        self.base_url = base_url.rstrip("/")
        self.system_user_id = system_user_id
        self.timeout = timeout

    async def job_skills(
        self, request_uuid: str, title: str, description: str, work_type: str
    ) -> list[str]:
        body = {"title": title, "description": description, "work_type": work_type}
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(
                    f"{self.base_url}{SKILL_EXTRACTION_PATH}",
                    json=body,
                    headers=self._headers(request_uuid),
                )
                response.raise_for_status()
                payload = response.json()
        except httpx.HTTPError as exc:
            raise MlError(f"skill-enhance request failed: {exc}") from exc
        except ValueError as exc:
            raise MlError(f"skill-enhance returned a non-JSON body: {exc}") from exc

        data = payload.get("data")
        if not isinstance(data, dict):
            raise MlError("skill-enhance returned an envelope without data")

        return [*_string_list(data, "skills"), *_string_list(data, "similarity_tags")]

    async def is_reachable(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(f"{self.base_url}/health")
                return response.status_code == 200
        except httpx.HTTPError:
            return False

    def _headers(self, request_uuid: str) -> dict[str, str]:
        return {
            "lynq-request-uuid": request_uuid,
            "user-id": self.system_user_id,
            "Content-Type": "application/json",
        }
