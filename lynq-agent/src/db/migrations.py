from __future__ import annotations

import asyncio
import logging
import os
import shutil
import subprocess
from dataclasses import dataclass
from urllib.parse import unquote, urlsplit

from config import get_settings

log = logging.getLogger(__name__)

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_SEARCH_PATH = os.path.join(_REPO_ROOT, "changelog")
_CHANGELOG_FILE = "db.changelog-config.xml"

DEFAULT_LIQUIBASE_HOME = "/opt/liquibase"
DEFAULT_MYSQL_PORT = 3306


class MigrationError(RuntimeError):
    pass


@dataclass
class JdbcTarget:
    url: str
    username: str
    password: str


def jdbc_target(db_url: str) -> JdbcTarget:
    parts = urlsplit(db_url)
    if not parts.hostname:
        raise MigrationError(f"DB_URL has no host: {parts.scheme}://...")

    database = parts.path.lstrip("/")
    if not database:
        raise MigrationError("DB_URL has no database name")

    url = f"jdbc:mysql://{parts.hostname}:{parts.port or DEFAULT_MYSQL_PORT}/{database}"
    if parts.query:
        url = f"{url}?{parts.query}"

    return JdbcTarget(
        url=url,
        username=unquote(parts.username or ""),
        password=unquote(parts.password or ""),
    )


def liquibase_executable() -> str:
    bundled = os.path.join(
        os.getenv("LIQUIBASE_HOME", DEFAULT_LIQUIBASE_HOME), "liquibase"
    )
    if os.access(bundled, os.X_OK):
        return bundled

    on_path = shutil.which("liquibase")
    if on_path:
        return on_path

    raise MigrationError(
        "liquibase was not found: set LIQUIBASE_HOME or put it on the PATH"
    )


def _command_environment(target: JdbcTarget) -> dict[str, str]:
    return {
        **os.environ,
        "LIQUIBASE_COMMAND_URL": target.url,
        "LIQUIBASE_COMMAND_USERNAME": target.username,
        "LIQUIBASE_COMMAND_PASSWORD": target.password,
        "LIQUIBASE_SEARCH_PATH": _SEARCH_PATH,
        "LIQUIBASE_COMMAND_CHANGELOG_FILE": _CHANGELOG_FILE,
        "LIQUIBASE_HEADLESS": "true",
        "LIQUIBASE_ANALYTICS_ENABLED": "false",
        "LIQUIBASE_SHOW_BANNER": "false",
    }


def _update_blocking(db_url: str) -> None:
    target = jdbc_target(db_url)
    result = subprocess.run(
        [liquibase_executable(), "update"],
        env=_command_environment(target),
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise MigrationError(
            f"liquibase update failed against {target.url} "
            f"(exit {result.returncode}): {result.stderr.strip() or result.stdout.strip()}"
        )
    log.debug("message= %s", result.stdout.strip())


async def update_to_latest() -> None:
    try:
        await asyncio.to_thread(_update_blocking, get_settings().db_url)
        log.info("message= Database schema is up to date")
    except Exception as exc:
        log.error("message= Database migration failed", exc_info=exc)
        raise
