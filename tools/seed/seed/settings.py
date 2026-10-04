from __future__ import annotations

import os
from dataclasses import dataclass, field
from urllib.parse import urlparse

LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}


class SeedConfigError(Exception):
    pass


def env(name: str, default: str) -> str:
    return os.environ.get(name) or default


@dataclass(frozen=True)
class Settings:
    bff_url: str = field(default_factory=lambda: env("LYNQ_BFF_URL", "http://localhost:8087/lynq-bff"))
    backend_url: str = field(default_factory=lambda: env("LYNQ_BACKEND_URL", "http://localhost:8082/lynq-backend-app"))
    llm_url: str = field(default_factory=lambda: env("LYNQ_LLM_URL", "http://localhost:8084/lynq-llm"))
    analytics_url: str = field(default_factory=lambda: env("LYNQ_ANALYTICS_URL", "http://localhost:8091/lynq-analytics"))
    http_timeout: float = 30.0
    render_timeout: float = 180.0
    llm_timeout: float = 360.0

    iam_url: str = field(default_factory=lambda: env("LYNQ_IAM_URL", "http://localhost:8080/lynq-iam"))
    file_storage_url: str = field(default_factory=lambda: env("LYNQ_FILE_STORAGE_URL", "http://localhost:8085"))
    redis_url: str = field(default_factory=lambda: env("SEED_REDIS_URL", "tcp://localhost:6379"))
    localstack_url: str = field(default_factory=lambda: env("SEED_LOCALSTACK_URL", "http://localhost:4566"))

    def service_urls(self) -> dict[str, str]:
        return {
            "lynq-bff": self.bff_url,
            "lynq-app-backend": self.backend_url,
            "lynq-llm": self.llm_url,
            "lynq-analytics": self.analytics_url,
        }

    def dependency_urls(self) -> dict[str, str]:
        return {
            "lynq-iam": self.iam_url,
            "lynq-file-storage": self.file_storage_url,
            "redis": self.redis_url,
            "localstack": self.localstack_url,
        }

    def ensure_local(self) -> None:
        if os.environ.get("SEED_ALLOW_REMOTE", "").lower() == "true":
            return
        remote = {name: url for name, url in self.service_urls().items()
                  if urlparse(url).hostname not in LOCAL_HOSTS}
        if remote:
            raise SeedConfigError(
                f"the seed only runs against localhost (a port-forward counts); these point elsewhere: {remote}. "
                "Set SEED_ALLOW_REMOTE=true only for the demo cluster, never for production.")

    def internal_token(self) -> str:
        token = os.environ.get("LYNQ_INTERNAL_TOKEN")
        if not token:
            raise SeedConfigError("LYNQ_INTERNAL_TOKEN is not set; load it with "
                                  "`set -a; . lynq-feeders/set_env.sh; set +a`")
        return token

    def seed_password(self) -> str:
        password = os.environ.get("LYNQ_SEED_PASSWORD")
        if not password:
            raise SeedConfigError("LYNQ_SEED_PASSWORD is not set: export the password the seed accounts use")
        if len(password) < 8:
            raise SeedConfigError("LYNQ_SEED_PASSWORD must have at least 8 characters, as lynq-iam requires")
        return password
