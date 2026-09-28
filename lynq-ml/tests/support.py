"""Shared helpers for the router tests.

Every DMZ route resolves its caller against lynq-iam, which a unit test has no
business reaching. The dependency is overridden instead, so a test states who is
calling rather than standing up an identity provider.
"""

from __future__ import annotations

from fastapi.testclient import TestClient

from main import app
from security import Principal, require_internal_caller, require_principal

USER_ID = "user-1"
COMPANY_ID = "company-1"
REQUEST_UUID = "req-123"
BEARER_TOKEN = "Bearer access-token"

HEADERS = {
    "lynq-request-uuid": REQUEST_UUID,
    "Authorization": BEARER_TOKEN,
    "company-id": COMPANY_ID,
}

INTERNAL_HEADERS = {
    "lynq-request-uuid": REQUEST_UUID,
    "lynq-internal-token": "internal-token",
    "user-id": USER_ID,
    "company-id": COMPANY_ID,
}


def principal(user_id: str = USER_ID, roles: list[str] | None = None) -> Principal:
    return Principal(
        id=user_id,
        username="janedoe",
        email="jane@lynq.com",
        roles=roles if roles is not None else ["R_CANDIDATE"],
    )


def authenticated_client(
    user_id: str = USER_ID, roles: list[str] | None = None
) -> TestClient:
    app.dependency_overrides[require_principal] = lambda: principal(user_id, roles)
    app.dependency_overrides[require_internal_caller] = lambda: None
    return TestClient(app)


def anonymous_client() -> TestClient:
    """A client with no caller resolved: the route has to turn it away itself."""
    clear_overrides()
    return TestClient(app)


def clear_overrides() -> None:
    app.dependency_overrides.pop(require_principal, None)
    app.dependency_overrides.pop(require_internal_caller, None)
