import unittest
import uuid
from unittest.mock import Mock

from seed.lynq import ROLE_CANDIDATE, Account, LynqApiError, Session, ingested_job_id


def conflict() -> LynqApiError:
    return LynqApiError("POST", "/auth/register", 409, "Username already exists")


class SessionTests(unittest.TestCase):
    def setUp(self):
        self.api = Mock()
        self.clock = Mock(return_value=0.0)
        self.account = Account("testuser1", "testuser1@lynq.test", ROLE_CANDIDATE)

    def test_registers_a_new_account_and_keeps_its_token(self):
        self.api.register.return_value = {"accessToken": "fresh", "id": "u1"}

        session = Session(self.api, self.account, "secret-password", self.clock).open()

        self.assertTrue(session.created)
        self.assertEqual("fresh", session.token())
        self.api.login.assert_not_called()

    def test_logs_in_when_the_username_already_exists(self):
        self.api.register.side_effect = conflict()
        self.api.login.return_value = {"accessToken": "existing"}

        session = Session(self.api, self.account, "secret-password", self.clock).open()

        self.assertFalse(session.created)
        self.assertEqual("existing", session.token())
        self.api.login.assert_called_once_with("testuser1", "secret-password")

    def test_reports_an_existing_account_with_another_password(self):
        self.api.register.side_effect = conflict()
        self.api.login.side_effect = LynqApiError("POST", "/auth/login/username", 403, "Invalid username or password")

        with self.assertRaises(LynqApiError) as raised:
            Session(self.api, self.account, "secret-password", self.clock).open()

        self.assertIn("different password", raised.exception.reason)

    def test_logs_in_again_once_the_token_is_old(self):
        self.api.register.return_value = {"accessToken": "first"}
        self.api.login.return_value = {"accessToken": "second"}
        session = Session(self.api, self.account, "secret-password", self.clock).open()

        self.clock.return_value = 11 * 60

        self.assertEqual("second", session.token())

    def test_retries_once_with_a_new_token_after_a_401(self):
        self.api.register.return_value = {"accessToken": "expired"}
        self.api.login.return_value = {"accessToken": "renewed"}
        session = Session(self.api, self.account, "secret-password", self.clock).open()
        operation = Mock(side_effect=[LynqApiError("GET", "/user", 401, "expired"), "done"])

        self.assertEqual("done", session.run(operation))
        self.assertEqual(["expired", "renewed"], [call.args[0] for call in operation.call_args_list])


class IngestedJobIdTests(unittest.TestCase):
    def test_uses_the_feeder_namespace_over_the_lowercased_source_and_external_id(self):
        namespace = uuid.uuid5(uuid.NAMESPACE_URL, "lynq.feeders")

        self.assertEqual(str(uuid.uuid5(namespace, "bumeran|abc")), ingested_job_id("BUMERAN", "abc"))
        self.assertNotEqual(ingested_job_id("BUMERAN", "abc"), ingested_job_id("COMPUTRABAJO", "abc"))


if __name__ == "__main__":
    unittest.main()
