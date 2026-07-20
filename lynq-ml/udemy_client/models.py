"""Value objects returned by the Udemy course-search client."""

from __future__ import annotations

from pydantic import BaseModel


class Course(BaseModel):
    """A single Udemy course result: its title and an openable link."""

    title: str
    url: str