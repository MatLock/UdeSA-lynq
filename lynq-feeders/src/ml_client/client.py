from __future__ import annotations

import logging

import httpx

log = logging.getLogger(__name__)

SKILL_ENHANCE_PATH = "/dmz/skill-enhance"


class MlError(RuntimeError):
    pass


class SkillEnhanceResult:

    def __init__(self, skills: list[str], similarity_tags: list[str]) -> None:
        self.skills = skills
        self.similarity_tags = similarity_tags

    @property
    def is_empty(self) -> bool:
        return not self.skills and not self.similarity_tags


EMPTY_RESULT = SkillEnhanceResult([], [])


def _string_list(payload: dict, key: str) -> list[str]:
    value = payload.get(key)
    if isinstance(value, list) and all(isinstance(item, str) for item in value):
        return value
    return []


class MlClient:

    def __init__(self, base_url: str, system_user_id: str, timeout: float) -> None:
        self.base_url = base_url.rstrip("/")
        self.system_user_id = system_user_id
        self.timeout = timeout

    def _headers(self, request_uuid: str) -> dict[str, str]:
        return {
            "lynq-request-uuid": request_uuid,
            "user-id": self.system_user_id,
            "Content-Type": "application/json",
        }

    async def skill_enhance(
        self, request_uuid: str, title: str, description: str, work_type: str
    ) -> SkillEnhanceResult:
        body = {"title": title, "description": description, "work_type": work_type}
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(
                    f"{self.base_url}{SKILL_ENHANCE_PATH}",
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

        return SkillEnhanceResult(
            skills=_string_list(data, "skills"),
            similarity_tags=_string_list(data, "similarity_tags"),
        )

    async def is_reachable(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(f"{self.base_url}/health")
            return response.status_code < 500
        except httpx.HTTPError:
            return False
