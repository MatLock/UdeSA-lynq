import unittest
from datetime import datetime, timezone
from unittest.mock import Mock

from seed.loader import (
    CANDIDATES_STEP,
    CLOSE_STEP,
    COMPANIES_STEP,
    EXTERNALS_STEP,
    VIEWS_STEP,
    Loader,
    posted_at_millis,
    resume_with_extracted_skills,
)
from seed.lynq import LynqApiError, ingested_job_id

NOW = datetime(2026, 10, 3, 15, 0, tzinfo=timezone.utc)


def corpus() -> dict:
    return {
        "companies": [{
            "key": "company-nimbus", "account": "testcompany1", "name": "Nimbus", "size": 180, "about": "Software.",
            "owner": {"full_name": "Victoria Romano", "position": "HRBP", "birth_date": "1980-11-27",
                      "about": "Busco perfiles técnicos."},
        }],
        "job_posts": [
            {"key": "lynq-001", "kind": "LYNQ", "source": "LYNQ", "company_key": "company-nimbus",
             "title": "Desarrollador/a Java", "description": "Java y Spring.", "work_type": "REMOTE",
             "salary_range_down": 3000, "salary_range_top": 4000, "salary_currency": "USD", "category": "TECNOLOGIA"},
            {"key": "lynq-002", "kind": "LYNQ", "source": "LYNQ", "company_key": "company-nimbus",
             "title": "Analista de Datos", "description": "SQL.", "work_type": "IN_OFFICE",
             "salary_range_down": None, "salary_range_top": None, "salary_currency": None, "category": "TECNOLOGIA"},
            {"key": "ext-0001", "kind": "EXTERNAL", "source": "BUMERAN", "external_id": "e1",
             "company_name": "Pampa Seguros", "title": "Analista Contable", "description": "Balances.",
             "work_type": "IN_OFFICE", "salary_range_down": 1500000, "salary_range_top": None,
             "salary_currency": "ARS", "category": "CONTABILIDAD", "job_url": "https://example.test/e1",
             "posted_days_ago": 10},
        ],
        "candidates": [{
            "key": "candidate-0001", "account": "testuser1", "email": "testuser1@lynq.test",
            "full_name": "Carolina Nakamura", "birth_date": "1993-12-09", "current_position": "Analista",
            "about": "Me gustan los impuestos.", "expected_salary": 3500000, "expected_salary_currency": "ARS",
            "template": "CLASSIC", "language": "ES",
            "resume": {"personal_info": {"full_name": "Carolina Nakamura"},
                       "skills": {"technical": ["IVA", "Excel"], "tools": [], "soft": []}},
        }],
        "applications": [{"candidate": "candidate-0001", "job": "lynq-001"},
                         {"candidate": "candidate-0001", "job": "ext-0001"}],
        "views": {"lynq-001": 3},
        "closures": ["lynq-002"],
    }


def fresh_api() -> Mock:
    api = Mock()
    api.register.return_value = {"accessToken": "token"}
    return api


class LoaderTests(unittest.TestCase):
    def loader(self, api: Mock, data: dict | None = None, refresh_externals: bool = False) -> Loader:
        return Loader(api, "secret-password", data or corpus(), Mock(), refresh_externals, now=lambda: NOW)

    def test_company_publishes_each_job_with_the_skills_lynq_suggests(self):
        api = fresh_api()
        api.get_user.return_value = None
        api.my_jobs.return_value = []
        api.enhance_job.return_value = {"skills": ["Java", "java", "Spring Boot"], "similarity_tags": ["Backend Development"]}
        api.create_job.side_effect = [{"jobId": "j1"}, {"jobId": "j2"}]

        report = self.loader(api).run((COMPANIES_STEP,))

        api.create_company.assert_called_once()
        api.enhance_job.assert_any_call("token", "Desarrollador/a Java", "Java y Spring.", "REMOTE")
        first_body = api.create_job.call_args_list[0].args[1]
        self.assertEqual(["Java", "Spring Boot"], first_body["skills"])
        self.assertEqual(["Backend Development"], first_body["similarityTags"])
        self.assertEqual("LYNQ", first_body["jobPostSource"])
        self.assertEqual("USD", first_body["salaryCurrency"])
        self.assertNotIn("salaryRangeDown", api.create_job.call_args_list[1].args[1])
        self.assertEqual(2, report.counters["lynq job posts published"])

    def test_company_skips_job_posts_it_already_published(self):
        api = fresh_api()
        api.get_user.return_value = {"companyId": "c1"}
        api.my_jobs.return_value = [{"title": "Desarrollador/a Java", "jobId": "j1", "jobPostSource": "LYNQ"},
                                    {"title": "Analista de Datos", "jobId": "j2", "jobPostSource": "LYNQ"}]

        self.loader(api).run((COMPANIES_STEP,))

        api.update_company.assert_called_once()
        api.enhance_job.assert_not_called()
        api.create_job.assert_not_called()

    def test_externals_go_through_the_feeder_path_with_their_posting_date(self):
        api = fresh_api()
        api.job_exists.return_value = False
        api.enhance_external_job.return_value = {"skills": ["Balances"], "similarity_tags": ["Financial Reporting"]}
        api.my_jobs.return_value = []

        self.loader(api).run((EXTERNALS_STEP,))

        api.enhance_external_job.assert_called_once_with("Analista Contable", "Balances.", "IN_OFFICE")
        payload = api.ingest_jobs.call_args.args[0][0]
        self.assertEqual("e1", payload["externalId"])
        self.assertEqual("CONTABILIDAD", payload["category"])
        self.assertEqual(posted_at_millis(10, NOW), payload["postedAt"])
        self.assertEqual(["Financial Reporting"], payload["similarityTags"])

    def test_externals_already_in_lynq_are_not_enhanced_again(self):
        api = fresh_api()
        api.job_exists.return_value = True
        api.my_jobs.return_value = []

        self.loader(api).run((EXTERNALS_STEP,))

        api.job_exists.assert_called_once_with("token", ingested_job_id("BUMERAN", "e1"))
        api.enhance_external_job.assert_not_called()
        api.ingest_jobs.assert_not_called()

    def test_externals_are_all_ingested_while_the_viewer_has_no_profile_yet(self):
        api = fresh_api()
        api.get_user.return_value = None
        api.enhance_external_job.return_value = {"skills": ["Balances"], "similarity_tags": []}
        api.my_jobs.return_value = []

        report = self.loader(api).run((EXTERNALS_STEP,))

        api.job_exists.assert_not_called()
        api.ingest_jobs.assert_called_once()
        self.assertEqual([], report.errors)

    def test_externals_without_any_skill_or_tag_are_reported_and_skipped(self):
        api = fresh_api()
        api.job_exists.return_value = False
        api.enhance_external_job.return_value = {"skills": [], "similarity_tags": []}
        api.my_jobs.return_value = []

        report = self.loader(api).run((EXTERNALS_STEP,))

        api.ingest_jobs.assert_not_called()
        self.assertEqual(1, len(report.errors))

    def test_candidate_builds_the_resume_like_the_wizard_and_applies(self):
        api = fresh_api()
        api.get_user.return_value = None
        api.my_jobs.return_value = [{"title": "Desarrollador/a Java", "jobId": "j1", "jobPostSource": "LYNQ"}]
        api.list_resumes.return_value = []
        api.extract_resume_skills.return_value = {"skills": ["iva", "Ganancias"], "tools": ["SIAP"],
                                                  "soft": ["Organización"], "similarity_tags": ["Tax Compliance"]}
        api.preview_resume.return_value = {"fileId": "f1", "pdfUrl": "url"}
        api.create_resume.return_value = {"id": "r1"}

        report = self.loader(api).run((CANDIDATES_STEP,))

        api.create_user.assert_called_once()
        api.update_user.assert_called_once_with("token", {"expectedSalary": 3500000, "expectedSalaryCurrency": "ARS"})
        api.extract_resume_skills.assert_called_once()
        self.assertEqual("es", api.extract_resume_skills.call_args.args[2])
        created = api.create_resume.call_args.args[1]
        self.assertEqual("f1", created["fileId"])
        self.assertEqual(["Tax Compliance"], created["similarityTags"])
        self.assertEqual(["IVA", "Excel", "Ganancias"], created["resume"]["skills"]["technical"])
        api.apply.assert_any_call("token", "j1", "r1")
        api.apply.assert_any_call("token", ingested_job_id("BUMERAN", "e1"), "r1")
        self.assertEqual(2, report.counters["applications submitted"])

    def test_candidate_reuses_the_resume_and_tolerates_previous_applications(self):
        api = fresh_api()
        api.get_user.return_value = {"id": "u1"}
        api.my_jobs.return_value = []
        api.list_resumes.return_value = [{"id": "r9", "name": "Carolina Nakamura", "language": "ES"}]
        api.apply.side_effect = LynqApiError("POST", "/apply", 400, "User has already applied to this job")

        report = self.loader(api).run((CANDIDATES_STEP,))

        api.extract_resume_skills.assert_not_called()
        api.preview_resume.assert_not_called()
        self.assertEqual(1, report.counters["applications already there"])
        self.assertEqual([], report.errors)

    def test_views_stop_once_the_target_is_reached(self):
        api = fresh_api()
        api.my_jobs.return_value = [{"title": "Desarrollador/a Java", "jobId": "j1", "jobPostSource": "LYNQ"}]
        api.increase_seen.side_effect = [1, 2, 3]

        report = self.loader(api).run((VIEWS_STEP,))

        self.assertEqual(3, api.increase_seen.call_count)
        self.assertEqual(3, report.counters["views added"])

    def test_closing_tolerates_job_posts_that_are_already_closed(self):
        api = fresh_api()
        api.my_jobs.return_value = [{"title": "Analista de Datos", "jobId": "j2", "jobPostSource": "LYNQ"}]
        api.close_job.side_effect = LynqApiError("PATCH", "/close", 400, "Only open job posts can be closed")

        report = self.loader(api).run((CLOSE_STEP,))

        api.close_job.assert_called_once_with("token", "j2")
        self.assertEqual(1, report.counters["lynq job posts already closed"])


class ResumeMergeTests(unittest.TestCase):
    def test_keeps_typed_skills_and_adds_extracted_ones_without_case_duplicates(self):
        resume = {"skills": {"technical": ["Excel", "IVA"], "tools": ["Tango"], "soft": []}}

        merged = resume_with_extracted_skills(resume, {"skills": ["excel", "Ganancias"], "tools": ["TANGO", "SIAP"],
                                                       "soft": ["Proactividad"]})

        self.assertEqual(["Excel", "IVA", "Ganancias"], merged["skills"]["technical"])
        self.assertEqual(["Tango", "SIAP"], merged["skills"]["tools"])
        self.assertEqual(["Proactividad"], merged["skills"]["soft"])
        self.assertEqual(["Excel", "IVA"], resume["skills"]["technical"])


if __name__ == "__main__":
    unittest.main()
