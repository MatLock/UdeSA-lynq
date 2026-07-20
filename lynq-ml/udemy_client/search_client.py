"""Keyless course provider: finds Udemy courses without an API key.

The Udemy Affiliate API is deprecated, so this provider asks a public web
search engine (DuckDuckGo's HTML endpoint) for pages under ``udemy.com/course/``
matching the topic, yielding *real* individual course links with no key.

Search engines rate-limit server traffic, so a lookup can come back empty
(HTTP 202 challenge, network error, or simply no hits). In that case the
provider falls back to a deterministic Udemy **search deep-link** for the topic
— never failing the request. Results are capped at ``max_courses`` per topic.
"""

from __future__ import annotations

import html
import logging
import re
import urllib.parse

import httpx

from .models import Course

log = logging.getLogger(__name__)

_DDG_HTML_URL = "https://html.duckduckgo.com/html/"

# Matches a canonical Udemy course page: https://www.udemy.com/course/<slug>/
_COURSE_URL_RE = re.compile(r"^https://www\.udemy\.com/course/[^/?#]+/?$")

# DuckDuckGo wraps each organic result target in a `uddg=<url-encoded>` redirect
# parameter; ad results use a different (`y.js`) redirect we skip via the regex.
_RESULT_ANCHOR_RE = re.compile(
    r'<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>',
    re.DOTALL,
)
_TAG_RE = re.compile(r"<[^>]+>")


class UdemySearchClient:
    """Finds Udemy courses via web search, with a search-link fallback."""

    def __init__(
        self,
        base_url: str = "https://www.udemy.com",
        max_courses: int = 2,
        timeout: float = 15.0,
        search_url: str = _DDG_HTML_URL,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.max_courses = max_courses
        self.timeout = timeout
        self.search_url = search_url

    async def search_courses(self, query: str) -> list[Course]:
        """Return up to ``max_courses`` courses for ``query``.

        Never raises for transport/parse problems: a failed or empty web search
        degrades to a single Udemy search deep-link for the topic.
        """
        courses = await self._search_engine_courses(query)
        if not courses:
            courses = [self._search_link_course(query)]
        return courses[: self.max_courses]

    async def _search_engine_courses(self, query: str) -> list[Course]:
        """Best-effort: scrape real course links from the search engine."""
        search_query = f"site:udemy.com/course {query}"
        try:
            async with httpx.AsyncClient(
                timeout=self.timeout, follow_redirects=True
            ) as http:
                response = await http.get(
                    self.search_url,
                    params={"q": search_query},
                    headers={"User-Agent": "Mozilla/5.0 (X11; Linux x86_64)"},
                )
            if response.status_code != 200:
                # 202 = anti-bot challenge / rate limit; anything non-200 → fall back.
                log.info(
                    "message= Course search engine returned status %s, "
                    "falling back to search link, query=%s",
                    response.status_code,
                    query,
                )
                return []
            return self._parse(response.text)
        except httpx.HTTPError as exc:
            log.info(
                "message= Course search engine unreachable, falling back to "
                "search link, query=%s, error=%s",
                query,
                exc,
            )
            return []

    def _parse(self, body: str) -> list[Course]:
        """Extract deduplicated ``(title, url)`` course results from HTML."""
        courses: list[Course] = []
        seen: set[str] = set()
        for href, text in _RESULT_ANCHOR_RE.findall(body):
            match = re.search(r"uddg=([^&\"]+)", href)
            if not match:
                continue
            url = urllib.parse.unquote(match.group(1))
            if not _COURSE_URL_RE.match(url) or url in seen:
                continue
            seen.add(url)
            title = html.unescape(_TAG_RE.sub("", text)).strip()
            courses.append(Course(title=title or url, url=url))
            if len(courses) >= self.max_courses:
                break
        return courses

    def _search_link_course(self, query: str) -> Course:
        """A deterministic, always-valid Udemy search deep-link for the topic."""
        params = urllib.parse.urlencode({"q": query, "sort": "relevance"})
        return Course(
            title=f"Udemy courses for '{query}'",
            url=f"{self.base_url}/courses/search/?{params}",
        )
