from __future__ import annotations

import unittest
from unittest.mock import patch

from config import DEFAULT_SYSTEM_USER_ID, get_settings


class SettingsTest(unittest.TestCase):

    def _settings(self, env):
        with patch.dict("os.environ", env, clear=True):
            return get_settings()

    def test_defaults_target_the_local_stack(self):
        settings = self._settings({})

        self.assertEqual(settings.ml_url, "http://localhost:8084/lynq-ml")
        self.assertEqual(settings.backend_url, "http://localhost:8082/lynq-backend-app")
        self.assertEqual(settings.jobs_per_rubro, 10)
        self.assertEqual(settings.system_user_id, DEFAULT_SYSTEM_USER_ID)

    def test_the_internal_token_is_empty_unless_configured(self):
        self.assertEqual(self._settings({}).internal_token, "")

    def test_default_rubros_and_sources(self):
        settings = self._settings({})

        self.assertEqual(
            settings.rubros,
            ["ADMINISTRACION", "TECNOLOGIA", "CONTABILIDAD", "RECURSOS_HUMANOS"],
        )
        self.assertEqual(settings.sources, ["bumeran", "computrabajo"])

    def test_trailing_slashes_are_stripped_from_urls(self):
        settings = self._settings({"LYNQ_ML_URL": "http://ml:8084/lynq-ml/"})
        self.assertEqual(settings.ml_url, "http://ml:8084/lynq-ml")

    def test_csv_settings_are_trimmed_and_emptied_entries_dropped(self):
        settings = self._settings({"FEEDER_RUBROS": " TECNOLOGIA , ,CONTABILIDAD "})
        self.assertEqual(settings.rubros, ["TECNOLOGIA", "CONTABILIDAD"])

    def test_a_malformed_number_falls_back_to_the_default(self):
        settings = self._settings({"FEEDER_JOBS_PER_RUBRO": "diez", "ML_TIMEOUT": "mucho"})

        self.assertEqual(settings.jobs_per_rubro, 10)
        self.assertEqual(settings.ml_timeout, 300.0)

    def test_overrides_are_honoured(self):
        settings = self._settings(
            {
                "FEEDER_JOBS_PER_RUBRO": "3",
                "FEEDER_SOURCES": "computrabajo",
                "ML_CONCURRENCY": "5",
                "LYNQ_INTERNAL_TOKEN": "configured",
            }
        )

        self.assertEqual(settings.jobs_per_rubro, 3)
        self.assertEqual(settings.sources, ["computrabajo"])
        self.assertEqual(settings.ml_concurrency, 5)
        self.assertEqual(settings.internal_token, "configured")


if __name__ == "__main__":
    unittest.main()
