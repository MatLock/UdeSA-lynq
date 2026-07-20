"""Keyless Udemy course-search provider and its config-driven factory.

:class:`UdemySearchClient` finds real Udemy course links via a public web
search (no API key), falling back to a Udemy search deep-link when the search
engine is unavailable. The Udemy Affiliate API is deprecated and is not used.
"""

from __future__ import annotations

import os

from .models import Course
from .search_client import UdemySearchClient

__all__ = ["UdemySearchClient", "Course", "get_course_provider"]


def get_course_provider() -> UdemySearchClient:
    """Build the keyless course-search provider from the environment.

    Reads:

    - ``UDEMY_MAX_COURSES`` (default ``2``) — max courses returned per topic.
    - ``UDEMY_BASE_URL`` (default ``https://www.udemy.com``).
    - ``COURSE_SEARCH_TIMEOUT`` (default ``15``) — web-search timeout, seconds.
    """
    return UdemySearchClient(
        base_url=os.getenv("UDEMY_BASE_URL", "https://www.udemy.com"),
        max_courses=int(os.getenv("UDEMY_MAX_COURSES", "2")),
        timeout=float(os.getenv("COURSE_SEARCH_TIMEOUT", "15")),
    )
