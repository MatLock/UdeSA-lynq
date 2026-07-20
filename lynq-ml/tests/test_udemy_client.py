"""Tests for the keyless course-search provider and its factory."""

from __future__ import annotations

import os
import unittest
from unittest.mock import patch

import httpx

from udemy_client import UdemySearchClient, get_course_provider

# --------------------------------------------------------------------------- #
# httpx.AsyncClient stand-in                                                    #
# --------------------------------------------------------------------------- #


class _FakeResponse:
    """Minimal stand-in for an ``httpx.Response``."""

    def __init__(self, text="", status_code=200):
        self.text = text
        self.status_code = status_code


class _FakeAsyncClient:
    """Async-context-manager stand-in that records the single ``get`` call."""

    last_call: dict = {}

    def __init__(self, response=None, error=None):
        self._response = response
        self._error = error

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_exc):
        return False

    async def get(self, url, params=None, headers=None):
        type(self).last_call = {"url": url, "params": params, "headers": headers}
        if self._error is not None:
            raise self._error
        return self._response


def _patch_search(*, response=None, error=None):
    fake = _FakeAsyncClient(response=response, error=error)
    return patch("udemy_client.search_client.httpx.AsyncClient", return_value=fake)


# --------------------------------------------------------------------------- #
# UdemySearchClient (keyless)                                                   #
# --------------------------------------------------------------------------- #

_DDG_HTML = """
<div>
  <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.udemy.com%2Fcourse%2Flearn-k8s%2F&rut=x">Learn <b>K8s</b></a>
  <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.udemy.com%2Fcourse%2Fadv-k8s%2F">Advanced K8s</a>
  <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.udemy.com%2Fcourse%2Fthird-k8s%2F">Third K8s</a>
  <a class="result__a" href="//duckduckgo.com/y.js?ad_domain=spam.com">An Ad</a>
</div>
"""


class UdemySearchClientTests(unittest.IsolatedAsyncioTestCase):
    def _client(self, max_courses=2):
        return UdemySearchClient(
            base_url="https://www.udemy.com", max_courses=max_courses
        )

    async def test_extracts_real_course_links_and_caps(self) -> None:
        with _patch_search(response=_FakeResponse(text=_DDG_HTML)):
            courses = await self._client(max_courses=2).search_courses("kubernetes")

        self.assertEqual(len(courses), 2)  # capped, ad skipped, third dropped
        self.assertEqual(courses[0].url, "https://www.udemy.com/course/learn-k8s/")
        self.assertEqual(courses[0].title, "Learn K8s")  # tags stripped, unescaped
        self.assertEqual(courses[1].url, "https://www.udemy.com/course/adv-k8s/")

    async def test_scopes_search_query_to_udemy_courses(self) -> None:
        with _patch_search(response=_FakeResponse(text="<div></div>")):
            await self._client().search_courses("graphql")

        self.assertEqual(
            _FakeAsyncClient.last_call["params"]["q"], "site:udemy.com/course graphql"
        )

    async def test_falls_back_to_search_link_on_rate_limit(self) -> None:
        # 202 = anti-bot challenge → deterministic search deep-link.
        with _patch_search(response=_FakeResponse(text="", status_code=202)):
            courses = await self._client().search_courses("terraform iac")

        self.assertEqual(len(courses), 1)
        self.assertIn("/courses/search/", courses[0].url)
        self.assertIn("q=terraform+iac", courses[0].url)

    async def test_falls_back_to_search_link_on_network_error(self) -> None:
        with _patch_search(error=httpx.ConnectError("down")):
            courses = await self._client().search_courses("graphql")

        self.assertEqual(len(courses), 1)
        self.assertIn("/courses/search/", courses[0].url)

    async def test_falls_back_when_no_course_results(self) -> None:
        with _patch_search(response=_FakeResponse(text="<div>no results</div>")):
            courses = await self._client().search_courses("obscure topic")

        self.assertEqual(len(courses), 1)
        self.assertIn("/courses/search/", courses[0].url)

    async def test_never_raises_on_failure(self) -> None:
        with _patch_search(error=httpx.ReadTimeout("t")):
            courses = await self._client().search_courses("x")
        self.assertTrue(courses)  # always returns at least the fallback link


# --------------------------------------------------------------------------- #
# get_course_provider factory                                                   #
# --------------------------------------------------------------------------- #


class GetCourseProviderTests(unittest.TestCase):
    def test_builds_keyless_provider_with_defaults(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            provider = get_course_provider()
        self.assertIsInstance(provider, UdemySearchClient)
        self.assertEqual(provider.max_courses, 2)
        self.assertEqual(provider.base_url, "https://www.udemy.com")

    def test_max_courses_is_configurable(self) -> None:
        with patch.dict(os.environ, {"UDEMY_MAX_COURSES": "5"}, clear=True):
            provider = get_course_provider()
        self.assertEqual(provider.max_courses, 5)

    def test_base_url_trailing_slash_is_stripped(self) -> None:
        with patch.dict(os.environ, {"UDEMY_BASE_URL": "https://example.test/"}, clear=True):
            provider = get_course_provider()
        self.assertEqual(provider.base_url, "https://example.test")


if __name__ == "__main__":
    unittest.main()
