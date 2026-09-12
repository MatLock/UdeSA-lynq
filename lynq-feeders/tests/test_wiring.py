from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import requests

from backend_client import BackendClient
from ml_client import MlClient
from router.ingest import build_service
from scraper import BumeranScraper, ComputrabajoScraper, get_scrapers
from scraper.computrabajo import ComputrabajoScraper as RawComputrabajoScraper


def _patched_get(response=None, side_effect=None):
    async_client = MagicMock()
    async_client.get = AsyncMock(return_value=response, side_effect=side_effect)
    context = MagicMock()
    context.__aenter__ = AsyncMock(return_value=async_client)
    context.__aexit__ = AsyncMock(return_value=False)
    return context


class GetScrapersTest(unittest.TestCase):

    def test_builds_the_scrapers_in_the_configured_order(self):
        scrapers = get_scrapers(["computrabajo", "bumeran"], timeout=5.0)

        self.assertIsInstance(scrapers[0], ComputrabajoScraper)
        self.assertIsInstance(scrapers[1], BumeranScraper)

    def test_source_names_are_case_and_space_insensitive(self):
        scrapers = get_scrapers([" Bumeran "], timeout=5.0)
        self.assertIsInstance(scrapers[0], BumeranScraper)

    def test_the_timeout_reaches_the_scraper(self):
        self.assertEqual(get_scrapers(["bumeran"], timeout=7.0)[0].timeout, 7.0)

    def test_an_unknown_source_is_rejected(self):
        with self.assertRaises(ValueError):
            get_scrapers(["linkedin"], timeout=5.0)


class ComputrabajoGetTest(unittest.TestCase):

    def setUp(self):
        self.scraper = RawComputrabajoScraper(timeout=1.0)
        self.session = MagicMock()
        self.scraper._session = self.session
        sleep = patch("scraper.computrabajo.time.sleep")
        sleep.start()
        self.addCleanup(sleep.stop)

    def _response(self, status_code, text="<html></html>"):
        response = MagicMock()
        response.status_code = status_code
        response.text = text
        response.raise_for_status = MagicMock(
            side_effect=requests.HTTPError(str(status_code)) if status_code >= 400 else None
        )
        return response

    def test_returns_the_body_on_success(self):
        self.session.get.return_value = self._response(200, "<html>ok</html>")
        self.assertEqual(self.scraper._get("https://example.com"), "<html>ok</html>")

    def test_backs_off_then_succeeds_on_a_403(self):
        self.session.get.side_effect = [self._response(403), self._response(200, "<html>ok</html>")]

        self.assertEqual(self.scraper._get("https://example.com"), "<html>ok</html>")
        self.assertEqual(self.session.get.call_count, 2)

    def test_gives_up_after_the_retry_budget(self):
        self.session.get.return_value = self._response(429)

        with self.assertRaises(RuntimeError):
            self.scraper._get("https://example.com")

        self.assertEqual(self.session.get.call_count, 4)

    def test_other_error_statuses_are_raised_immediately(self):
        self.session.get.return_value = self._response(500)

        with self.assertRaises(requests.HTTPError):
            self.scraper._get("https://example.com")

        self.assertEqual(self.session.get.call_count, 1)

    def test_the_session_is_created_once_and_reused(self):
        scraper = RawComputrabajoScraper(timeout=1.0)
        first = scraper._ensure_session()
        self.assertIs(first, scraper._ensure_session())


class BumeranSessionTest(unittest.TestCase):

    def test_the_session_warms_up_once_and_is_reused(self):
        scraper = BumeranScraper(timeout=1.0)
        with patch.object(scraper, "_warmup") as warmup:
            first = scraper._ensure_session()
            second = scraper._ensure_session()

        self.assertIs(first, second)
        warmup.assert_called_once()


class ReachabilityTest(unittest.IsolatedAsyncioTestCase):

    async def test_ml_is_reachable_when_health_answers(self):
        response = MagicMock()
        response.status_code = 200
        with patch("ml_client.client.httpx.AsyncClient", return_value=_patched_get(response)):
            client = MlClient("http://ml/lynq-ml", "system", 1.0)
            self.assertTrue(await client.is_reachable())

    async def test_ml_is_unreachable_on_a_transport_error(self):
        context = _patched_get(side_effect=httpx.ConnectError("refused"))
        with patch("ml_client.client.httpx.AsyncClient", return_value=context):
            client = MlClient("http://ml/lynq-ml", "system", 1.0)
            self.assertFalse(await client.is_reachable())

    async def test_ml_is_unreachable_when_health_returns_a_server_error(self):
        response = MagicMock()
        response.status_code = 503
        with patch("ml_client.client.httpx.AsyncClient", return_value=_patched_get(response)):
            client = MlClient("http://ml/lynq-ml", "system", 1.0)
            self.assertFalse(await client.is_reachable())

    async def test_backend_is_reachable_when_the_server_answers_at_all(self):
        response = MagicMock()
        response.status_code = 403
        with patch("backend_client.client.httpx.AsyncClient", return_value=_patched_get(response)):
            client = BackendClient("http://backend/lynq-backend-app", "token", 1.0)
            self.assertTrue(await client.is_reachable())

    async def test_backend_is_unreachable_on_a_transport_error(self):
        context = _patched_get(side_effect=httpx.ConnectError("refused"))
        with patch("backend_client.client.httpx.AsyncClient", return_value=context):
            client = BackendClient("http://backend/lynq-backend-app", "token", 1.0)
            self.assertFalse(await client.is_reachable())


class BuildServiceTest(unittest.TestCase):

    def test_wires_the_clients_from_the_environment(self):
        env = {
            "LYNQ_ML_URL": "http://ml:8084/lynq-ml",
            "LYNQ_BACKEND_URL": "http://backend:8080/lynq-backend-app",
            "LYNQ_INTERNAL_TOKEN": "configured",
            "FEEDER_SOURCES": "bumeran",
        }
        with patch.dict("os.environ", env, clear=True):
            service = build_service()

        self.assertEqual(service.ml_client.base_url, "http://ml:8084/lynq-ml")
        self.assertEqual(service.backend_client.base_url, "http://backend:8080/lynq-backend-app")
        self.assertEqual(service.backend_client.internal_token, "configured")
        self.assertEqual(len(service.scrapers_for(service.plan_for(None))), 1)


if __name__ == "__main__":
    unittest.main()
