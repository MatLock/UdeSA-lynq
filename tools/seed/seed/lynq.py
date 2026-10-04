from __future__ import annotations

import time
import uuid
from dataclasses import dataclass
from typing import Any, Callable

import httpx

from seed.settings import Settings

ROLE_CANDIDATE = "R_CANDIDATE"
ROLE_COMPANY = "R_COMPANY"

FEEDER_NAMESPACE = uuid.UUID("274b7337-c645-50e5-9f69-dbd36c3428a2")
FEEDER_SYSTEM_USER_ID = "00000000-0000-0000-0000-00000000feed"

TOKEN_MAX_AGE_SECONDS = 10 * 60


class LynqApiError(Exception):
    def __init__(self, method: str, url: str, status: int, reason: str | None):
        super().__init__(f"{method} {url} -> {status}: {reason}")
        self.status = status
        self.reason = reason or ""


def ingested_job_id(source: str, external_id: str) -> str:
    return str(uuid.uuid5(FEEDER_NAMESPACE, f"{source.lower()}|{external_id}"))


class LynqApi:
    def __init__(self, settings: Settings, http: httpx.Client | None = None):
        self.settings = settings
        self.http = http or httpx.Client(timeout=settings.http_timeout)

    def call(self, method: str, url: str, *, token: str | None = None, internal: bool = False,
             body: Any = None, params: dict | None = None, timeout: float | None = None,
             headers: dict | None = None) -> Any:
        request_headers = {"lynq-request-uuid": str(uuid.uuid4()), **(headers or {})}
        if token:
            request_headers["Authorization"] = f"Bearer {token}"
        if internal:
            request_headers["lynq-internal-token"] = self.settings.internal_token()
        try:
            response = self.http.request(method, url, headers=request_headers, json=body, params=params,
                                         timeout=timeout or self.settings.http_timeout)
        except httpx.HTTPError as error:
            raise LynqApiError(method, url, 0, f"{type(error).__name__}: {error}") from error
        payload = None
        if response.content:
            try:
                payload = response.json()
            except ValueError:
                payload = None
        if response.status_code >= 400:
            reason = payload.get("reason") if isinstance(payload, dict) else response.text[:300]
            if isinstance(payload, dict) and isinstance(payload.get("data"), dict):
                reason = f"{reason} {payload['data']}"
            raise LynqApiError(method, url, response.status_code, reason)
        if isinstance(payload, dict) and "success" in payload:
            return payload.get("data")
        return payload

    def bff(self, path: str) -> str:
        return f"{self.settings.bff_url}{path}"

    def register(self, username: str, email: str, password: str, role: str) -> dict:
        return self.call("POST", self.bff("/auth/register"),
                         body={"username": username, "email": email, "password": password, "role": role})

    def login(self, username: str, password: str) -> dict:
        return self.call("POST", self.bff("/auth/login/username"),
                         body={"username": username, "password": password})

    def get_user(self, token: str) -> dict | None:
        try:
            return self.call("GET", self.bff("/user"), token=token)
        except LynqApiError as error:
            if error.status == 404:
                return None
            raise

    def create_user(self, token: str, body: dict) -> dict:
        return self.call("POST", self.bff("/user"), token=token, body=body)

    def update_user(self, token: str, body: dict) -> dict:
        return self.call("PATCH", self.bff("/user"), token=token, body=body)

    def create_company(self, token: str, body: dict) -> dict:
        return self.call("POST", self.bff("/company"), token=token, body=body)

    def update_company(self, token: str, body: dict) -> dict:
        return self.call("PATCH", self.bff("/company"), token=token, body=body)

    def my_jobs(self, token: str) -> list[dict]:
        jobs: list[dict] = []
        page = 0
        while True:
            data = self.call("GET", self.bff("/job/mine"), token=token, params={"page": page, "size": 100})
            jobs.extend(data.get("content") or [])
            if not data.get("hasNext"):
                return jobs
            page += 1

    def enhance_job(self, token: str, title: str, description: str, work_type: str) -> dict:
        return self.call("POST", self.bff("/skill-enhance"), token=token,
                         body={"title": title, "description": description, "work_type": work_type},
                         timeout=self.settings.llm_timeout)

    def create_job(self, token: str, body: dict) -> dict:
        return self.call("POST", self.bff("/job"), token=token, body=body)

    def job_exists(self, token: str, job_id: str) -> bool:
        try:
            self.call("GET", self.bff(f"/job/{job_id}/details"), token=token)
            return True
        except LynqApiError as error:
            if error.status == 404:
                return False
            raise

    def close_job(self, token: str, job_id: str) -> dict:
        return self.call("PATCH", self.bff(f"/job/{job_id}/close"), token=token)

    def increase_seen(self, token: str, job_id: str) -> int:
        return int(self.call("PATCH", self.bff(f"/job/{job_id}/increase-seen"), token=token))

    def apply(self, token: str, job_id: str, resume_id: str) -> dict:
        return self.call("POST", self.bff(f"/job/{job_id}/apply"), token=token, body={"resumeId": resume_id})

    def list_resumes(self, token: str) -> list[dict]:
        return self.call("GET", self.bff("/user/resume"), token=token) or []

    def extract_resume_skills(self, token: str, resume: dict, language: str) -> dict:
        return self.call("POST", self.bff("/resume/skill-extraction"), token=token, body=resume,
                         params={"language": language}, timeout=self.settings.llm_timeout)

    def preview_resume(self, token: str, resume: dict, template: str) -> dict:
        return self.call("POST", self.bff("/resume/preview"), token=token,
                         body={"resume": resume, "template": template}, timeout=self.settings.render_timeout)

    def create_resume(self, token: str, body: dict) -> dict:
        return self.call("POST", self.bff("/user/resume"), token=token, body=body)

    def enhance_external_job(self, title: str, description: str, work_type: str) -> dict:
        return self.call("POST", f"{self.settings.llm_url}/internal/skill-enhance", internal=True,
                         body={"title": title, "description": description, "work_type": work_type},
                         headers={"user-id": FEEDER_SYSTEM_USER_ID}, timeout=self.settings.llm_timeout)

    def ingest_jobs(self, job_posts: list[dict]) -> dict:
        return self.call("POST", f"{self.settings.backend_url}/internal/job-posts/ingest", internal=True,
                         body={"jobPosts": job_posts})

    def replay_events(self) -> dict:
        return self.call("POST", f"{self.settings.backend_url}/internal/events/replay", internal=True,
                         timeout=self.settings.render_timeout)

    def snapshot(self) -> Any:
        return self.call("POST", f"{self.settings.analytics_url}/internal/snapshot", internal=True)


@dataclass
class Account:
    username: str
    email: str
    role: str


class Session:
    def __init__(self, api: LynqApi, account: Account, password: str, clock: Callable[[], float] = time.monotonic):
        self.api = api
        self.account = account
        self.password = password
        self.clock = clock
        self.created = False
        self._token: str | None = None
        self._issued_at = 0.0

    def open(self) -> "Session":
        try:
            data = self.api.register(self.account.username, self.account.email, self.password, self.account.role)
            self.created = True
            self._remember(data)
        except LynqApiError as error:
            if error.status != 409:
                raise
            self.login()
        return self

    def login(self) -> str:
        try:
            data = self.api.login(self.account.username, self.password)
        except LynqApiError as error:
            if error.status == 403:
                raise LynqApiError("POST", "/auth/login/username", 403,
                                   f"{self.account.username} already exists with a different password") from error
            raise
        self._remember(data)
        return self._token

    def token(self) -> str:
        if self._token is None or self.clock() - self._issued_at > TOKEN_MAX_AGE_SECONDS:
            return self.login()
        return self._token

    def run(self, operation: Callable[[str], Any]) -> Any:
        try:
            return operation(self.token())
        except LynqApiError as error:
            if error.status != 401:
                raise
            return operation(self.login())

    def _remember(self, data: dict) -> None:
        self._token = data["accessToken"]
        self._issued_at = self.clock()
