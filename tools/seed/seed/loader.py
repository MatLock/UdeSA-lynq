from __future__ import annotations

import copy
from dataclasses import dataclass, field
from datetime import datetime, time, timedelta, timezone
from typing import Callable

from seed.lynq import ROLE_CANDIDATE, ROLE_COMPANY, Account, LynqApi, LynqApiError, Session, ingested_job_id

LYNQ = "LYNQ"

COMPANIES_STEP = "companies"
EXTERNALS_STEP = "externals"
CANDIDATES_STEP = "candidates"
VIEWS_STEP = "views"
CLOSE_STEP = "close"
STEPS = (COMPANIES_STEP, EXTERNALS_STEP, CANDIDATES_STEP, VIEWS_STEP, CLOSE_STEP)

INGEST_BATCH_SIZE = 20
ALREADY_APPLIED = "already applied"
ONLY_OPEN_CAN_CLOSE = "only open job posts"

Log = Callable[[str], None]


@dataclass
class LoadReport:
    counters: dict[str, int] = field(default_factory=dict)
    errors: list[str] = field(default_factory=list)

    def count(self, name: str, amount: int = 1) -> None:
        self.counters[name] = self.counters.get(name, 0) + amount

    def summary(self) -> str:
        lines = [f"  {name}: {value}" for name, value in sorted(self.counters.items())]
        if self.errors:
            lines.append(f"  errors: {len(self.errors)}")
            lines.extend(f"    - {error}" for error in self.errors)
        return "\n".join(lines)


def unique_ignoring_case(values) -> list[str]:
    seen: set[str] = set()
    unique: list[str] = []
    for value in values or []:
        entry = value.strip() if isinstance(value, str) else ""
        if entry and entry.lower() not in seen:
            seen.add(entry.lower())
            unique.append(entry)
    return unique


def posted_at_millis(days_ago: int, now: datetime) -> int:
    day = now.date() - timedelta(days=days_ago)
    return int(datetime.combine(day, time(12, 0), tzinfo=timezone.utc).timestamp() * 1000)


def lynq_job_body(job: dict, enhanced: dict) -> dict:
    body = {
        "title": job["title"],
        "description": job["description"],
        "workType": job["work_type"],
        "jobPostSource": LYNQ,
        "skills": unique_ignoring_case(enhanced.get("skills")),
        "similarityTags": unique_ignoring_case(enhanced.get("similarity_tags")),
    }
    if job["salary_range_down"] is not None:
        body["salaryRangeDown"] = job["salary_range_down"]
    if job["salary_range_top"] is not None:
        body["salaryRangeTop"] = job["salary_range_top"]
    if job["salary_currency"]:
        body["salaryCurrency"] = job["salary_currency"]
    return body


def external_job_body(job: dict, enhanced: dict, now: datetime) -> dict:
    return {
        "externalId": job["external_id"],
        "title": job["title"],
        "description": job["description"],
        "workType": job["work_type"],
        "salaryRangeDown": job["salary_range_down"],
        "salaryRangeTop": job["salary_range_top"],
        "salaryCurrency": job["salary_currency"],
        "category": job["category"],
        "jobUrl": job["job_url"],
        "jobPostSource": job["source"],
        "companyName": job["company_name"],
        "companyLogoUrl": None,
        "postedAt": posted_at_millis(job["posted_days_ago"], now),
        "skills": unique_ignoring_case(enhanced.get("skills")),
        "similarityTags": unique_ignoring_case(enhanced.get("similarity_tags")),
    }


def resume_with_extracted_skills(resume: dict, extracted: dict) -> dict:
    merged = copy.deepcopy(resume)
    typed = merged.get("skills") or {}
    merged["skills"] = {
        "technical": unique_ignoring_case(list(typed.get("technical") or []) + list(extracted.get("skills") or [])),
        "tools": unique_ignoring_case(list(typed.get("tools") or []) + list(extracted.get("tools") or [])),
        "soft": unique_ignoring_case(list(typed.get("soft") or []) + list(extracted.get("soft") or [])),
    }
    return merged


class Loader:
    def __init__(self, api: LynqApi, password: str, corpus: dict, log: Log, refresh_externals: bool = False,
                 now: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self.api = api
        self.password = password
        self.corpus = corpus
        self.log = log
        self.refresh_externals = refresh_externals
        self.now = now
        self.report = LoadReport()
        self.sessions: dict[str, Session] = {}
        self.job_ids: dict[str, str] = {}
        self.jobs_by_key = {job["key"]: job for job in corpus["job_posts"]}
        self.companies_by_key = {company["key"]: company for company in corpus["companies"]}
        self.candidates_by_key = {candidate["key"]: candidate for candidate in corpus["candidates"]}

    def run(self, steps: tuple[str, ...]) -> LoadReport:
        for job in self.corpus["job_posts"]:
            if job["kind"] != LYNQ:
                self.job_ids[job["key"]] = ingested_job_id(job["source"], job["external_id"])
        if COMPANIES_STEP in steps:
            self.load_companies()
        else:
            self.resolve_lynq_job_ids()
        if EXTERNALS_STEP in steps:
            self.load_externals()
        if CANDIDATES_STEP in steps:
            self.load_candidates()
        if VIEWS_STEP in steps:
            self.load_views()
        if CLOSE_STEP in steps:
            self.close_jobs()
        return self.report

    def session(self, username: str, email: str, role: str) -> Session:
        session = self.sessions.get(username)
        if session is None:
            session = Session(self.api, Account(username, email, role), self.password).open()
            self.report.count("accounts registered" if session.created else "accounts reused")
            self.sessions[username] = session
        return session

    def company_session(self, company: dict) -> Session:
        return self.session(company["account"], f"{company['account']}@lynq.test", ROLE_COMPANY)

    def candidate_session(self, candidate: dict) -> Session:
        return self.session(candidate["account"], candidate["email"], ROLE_CANDIDATE)

    def fail(self, context: str, error: Exception) -> None:
        self.report.errors.append(f"{context}: {error}")
        self.log(f"  ! {context}: {error}")

    def own_jobs(self, company: dict) -> list[dict]:
        return [job for job in self.corpus["job_posts"]
                if job["kind"] == LYNQ and job["company_key"] == company["key"]]

    def existing_lynq_jobs(self, session: Session) -> dict[str, str]:
        return {job["title"]: job["jobId"] for job in session.run(self.api.my_jobs)
                if job.get("jobPostSource") == LYNQ}

    def resolve_lynq_job_ids(self) -> None:
        for company in self.corpus["companies"]:
            try:
                existing = self.existing_lynq_jobs(self.company_session(company))
            except LynqApiError as error:
                self.fail(f"jobs of {company['account']}", error)
                continue
            for job in self.own_jobs(company):
                if job["title"] in existing:
                    self.job_ids[job["key"]] = existing[job["title"]]

    def load_companies(self) -> None:
        self.log(f"companies: {len(self.corpus['companies'])} accounts, each publishing its LYNQ job posts")
        for company in self.corpus["companies"]:
            try:
                self.load_company(company)
            except LynqApiError as error:
                self.fail(f"company {company['account']}", error)

    def load_company(self, company: dict) -> None:
        session = self.company_session(company)
        owner = company["owner"]
        user = session.run(self.api.get_user)
        if user and user.get("companyId"):
            session.run(lambda token: self.api.update_company(
                token, {"name": company["name"], "about": company["about"], "size": company["size"]}))
            self.report.count("companies updated")
        else:
            session.run(lambda token: self.api.create_company(token, {
                "fullName": owner["full_name"],
                "currentPosition": owner["position"],
                "userAbout": owner["about"],
                "birthDate": owner["birth_date"],
                "companyName": company["name"],
                "companyAbout": company["about"],
                "companySize": company["size"],
            }))
            self.report.count("companies created")

        existing = self.existing_lynq_jobs(session)
        jobs = self.own_jobs(company)
        for index, job in enumerate(jobs, start=1):
            if job["title"] in existing:
                self.job_ids[job["key"]] = existing[job["title"]]
                self.report.count("lynq job posts already there")
                continue
            try:
                enhanced = session.run(lambda token: self.api.enhance_job(
                    token, job["title"], job["description"], job["work_type"]))
                created = session.run(lambda token: self.api.create_job(token, lynq_job_body(job, enhanced)))
            except LynqApiError as error:
                self.fail(f"job {job['key']}", error)
                continue
            self.job_ids[job["key"]] = created["jobId"]
            existing[job["title"]] = created["jobId"]
            self.report.count("lynq job posts published")
            self.log(f"  {company['account']} [{index}/{len(jobs)}] {job['title']}: "
                     f"{len(enhanced.get('skills') or [])} skills, {len(enhanced.get('similarity_tags') or [])} tags")

    def load_externals(self) -> None:
        externals = [job for job in self.corpus["job_posts"] if job["kind"] != LYNQ]
        viewer = None if self.refresh_externals else self.viewer()
        pending = externals
        if viewer is not None and viewer.run(self.api.get_user) is not None:
            pending = [job for job in externals
                       if not viewer.run(lambda token: self.api.job_exists(token, self.job_ids[job["key"]]))]
        self.log(f"external job posts: {len(externals)}, {len(pending)} to enhance and ingest like the feeder")
        now = self.now()
        batch: list[dict] = []
        for index, job in enumerate(pending, start=1):
            try:
                enhanced = self.api.enhance_external_job(job["title"], job["description"], job["work_type"])
            except LynqApiError as error:
                self.fail(f"enhance {job['key']}", error)
                continue
            if not enhanced.get("skills") and not enhanced.get("similarity_tags"):
                self.fail(f"enhance {job['key']}", ValueError("lynq-llm returned no skills and no tags"))
                continue
            batch.append(external_job_body(job, enhanced, now))
            self.log(f"  [{index}/{len(pending)}] {job['title']} @ {job['company_name']}: "
                     f"{len(enhanced.get('skills') or [])} skills, {len(enhanced.get('similarity_tags') or [])} tags")
            if len(batch) == INGEST_BATCH_SIZE:
                self.ingest(batch)
                batch = []
        if batch:
            self.ingest(batch)

    def ingest(self, batch: list[dict]) -> None:
        try:
            result = self.api.ingest_jobs(batch)
        except LynqApiError as error:
            self.fail(f"ingest of {len(batch)} job posts", error)
            return
        self.report.count("external job posts ingested", len(batch))
        self.log(f"  ingested {len(batch)}: {result}")

    def load_candidates(self) -> None:
        candidates = self.corpus["candidates"]
        applications: dict[str, list[str]] = {}
        for application in self.corpus["applications"]:
            applications.setdefault(application["candidate"], []).append(application["job"])
        self.log(f"candidates: {len(candidates)}, applications: {len(self.corpus['applications'])}")
        for index, candidate in enumerate(candidates, start=1):
            try:
                self.load_candidate(candidate, applications.get(candidate["key"], []))
            except LynqApiError as error:
                self.fail(f"candidate {candidate['account']}", error)
            self.log(f"  [{index}/{len(candidates)}] {candidate['account']} · {candidate['full_name']}")

    def load_candidate(self, candidate: dict, job_keys: list[str]) -> None:
        session = self.candidate_session(candidate)
        profile = {
            "fullName": candidate["full_name"],
            "currentPosition": candidate["current_position"],
            "about": candidate["about"],
            "birthDate": candidate["birth_date"],
        }
        if session.run(self.api.get_user) is None:
            session.run(lambda token: self.api.create_user(token, profile))
            self.report.count("candidate profiles created")
        else:
            session.run(lambda token: self.api.update_user(token, profile))
            self.report.count("candidate profiles updated")
        if candidate["expected_salary"]:
            session.run(lambda token: self.api.update_user(token, {
                "expectedSalary": candidate["expected_salary"],
                "expectedSalaryCurrency": candidate["expected_salary_currency"],
            }))

        resume_id = self.ensure_resume(session, candidate)
        for job_key in job_keys:
            job_id = self.job_ids.get(job_key)
            if job_id is None:
                continue
            try:
                session.run(lambda token: self.api.apply(token, job_id, resume_id))
                self.report.count("applications submitted")
            except LynqApiError as error:
                if error.status == 400 and ALREADY_APPLIED in error.reason.lower():
                    self.report.count("applications already there")
                    continue
                self.fail(f"apply {candidate['account']} -> {job_key}", error)

    def ensure_resume(self, session: Session, candidate: dict) -> str:
        for resume in session.run(self.api.list_resumes):
            if resume.get("language") == candidate["language"] and resume.get("name") == candidate["full_name"]:
                self.report.count("resumes already there")
                return resume["id"]
        extracted = session.run(lambda token: self.api.extract_resume_skills(
            token, candidate["resume"], candidate["language"].lower()))
        resume = resume_with_extracted_skills(candidate["resume"], extracted)
        preview = session.run(lambda token: self.api.preview_resume(token, resume, candidate["template"]))
        created = session.run(lambda token: self.api.create_resume(token, {
            "name": candidate["full_name"],
            "language": candidate["language"],
            "resume": resume,
            "fileId": preview["fileId"],
            "similarityTags": unique_ignoring_case(extracted.get("similarity_tags")),
        }))
        self.report.count("resumes created")
        return created["id"]

    def viewer(self) -> Session | None:
        for candidate in self.corpus["candidates"]:
            try:
                return self.candidate_session(candidate)
            except LynqApiError as error:
                self.fail(f"viewer {candidate['account']}", error)
        return None

    def load_views(self) -> None:
        session = self.viewer()
        if session is None:
            return
        targets = {key: views for key, views in self.corpus["views"].items() if key in self.job_ids}
        self.log(f"views: up to {sum(targets.values())} across {len(targets)} job posts")
        for index, (job_key, target) in enumerate(sorted(targets.items()), start=1):
            job_id = self.job_ids[job_key]
            try:
                seen = session.run(lambda token: self.api.increase_seen(token, job_id))
                calls = 1
                while seen < target:
                    seen = session.run(lambda token: self.api.increase_seen(token, job_id))
                    calls += 1
                self.report.count("views added", calls)
            except LynqApiError as error:
                self.fail(f"views {job_key}", error)
            if index % 50 == 0:
                self.log(f"  {index}/{len(targets)} job posts viewed")

    def close_jobs(self) -> None:
        self.log(f"closing {len(self.corpus['closures'])} LYNQ job posts as their owners")
        for job_key in self.corpus["closures"]:
            job_id = self.job_ids.get(job_key)
            if job_id is None:
                continue
            company = self.companies_by_key[self.jobs_by_key[job_key]["company_key"]]
            try:
                self.company_session(company).run(lambda token: self.api.close_job(token, job_id))
                self.report.count("lynq job posts closed")
            except LynqApiError as error:
                if error.status == 400 and ONLY_OPEN_CAN_CLOSE in error.reason.lower():
                    self.report.count("lynq job posts already closed")
                    continue
                self.fail(f"close {job_key}", error)
