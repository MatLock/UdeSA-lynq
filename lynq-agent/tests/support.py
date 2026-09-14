from __future__ import annotations

import os
import sys
import tempfile
import warnings

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), "src"))

from sqlalchemy.exc import SAWarning
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from db.models import Base

warnings.filterwarnings("ignore", category=SAWarning)

JOB = {
    "id": "job-1",
    "title": "Senior Backend Engineer",
    "company": "Acme",
    "description": "Buscamos backend con Kubernetes, PostgreSQL y Jenkins.",
    "work_type": "REMOTE",
    "skills": ["Kubernetes", "PostgreSQL", "Jenkins", "Go"],
    "similarity_tags": ["Container Orchestration"],
}


def base_resume() -> dict:
    return {
        "personal_info": {
            "full_name": "Ada Lovelace",
            "email": "ada@example.com",
            "phone": "+54 11 5555 5555",
            "links": {"github": "https://github.com/ada"},
        },
        "summary": "Backend con 8 años de experiencia en sistemas distribuidos.",
        "work_experience": [
            {
                "company": "Globant",
                "position": "Backend Engineer",
                "start_date": "2020-01",
                "end_date": "2023-06",
                "is_current": False,
                "description": "Armé los pipelines en Jenkins y desplegué en K8s.",
                "achievements": ["Bajé el tiempo de deploy a la mitad"],
                "technologies": ["Java", "Postgres"],
            },
            {
                "company": "Mercado Libre",
                "position": "Semi Senior Developer",
                "start_date": "2018-02",
                "end_date": "2019-12",
                "is_current": False,
                "description": "Servicios de facturación en Java.",
                "achievements": [],
                "technologies": ["Java"],
            },
        ],
        "education": [
            {
                "institution": "UBA",
                "degree": "Licenciatura en Sistemas",
                "start_date": "2013",
                "end_date": "2018",
                "is_current": False,
            }
        ],
        "skills": {
            "technical": ["Java", "Postgres"],
            "tools": ["Docker"],
            "soft": ["Trabajo en equipo"],
        },
        "languages": [{"language": "Español", "proficiency": "Nativo"}],
        "certifications": [],
        "projects": [],
    }


class TemporaryDatabase:

    def __init__(self) -> None:
        self._handle, self._path = tempfile.mkstemp(suffix=".sqlite")
        os.close(self._handle)
        self.url = f"sqlite+aiosqlite:///{self._path}"
        self.engine = create_async_engine(self.url)
        self.session_factory = async_sessionmaker(
            bind=self.engine, expire_on_commit=False
        )

    async def create_schema(self) -> None:
        async with self.engine.begin() as connection:
            await connection.run_sync(Base.metadata.create_all)

    async def dispose(self) -> None:
        await self.engine.dispose()
        try:
            os.unlink(self._path)
        except FileNotFoundError:
            pass
