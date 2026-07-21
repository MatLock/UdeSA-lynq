// Job service — talks to the secured app-backend (lynq-backend-app).
// Spec: lynq-app-backend JobController (GET /job).

/**
 * Fetch a page of available job posts, newest first.
 *
 * Calls GET /job (JobController.getJobs) through the caller's `authFetch` (see
 * useApi), which injects the bearer token and `lynq-request-uuid` header and —
 * crucially — refreshes the access token and retries once when it has expired
 * (backend replies 401 "Invalid or expired access token"). A single free-text
 * `filterValue` is matched (case-insensitive, contains) across the job title,
 * description, company name, work type and skills; omit it to list everything.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   authenticated fetcher from useApi. Returns the parsed GlobalRestResponse
 *   envelope and throws on a non-OK response (with `status`/`reason`).
 * @param {object} [params] - Query parameters.
 * @param {number} [params.page=0] - Zero-based page index.
 * @param {number} [params.size=20] - Page size.
 * @param {string} [params.filterValue] - Free-text search; omitted when blank.
 * @returns {Promise<{
 *   content: Array<{
 *     jobId: string,
 *     title: string,
 *     description: string,
 *     workType: 'REMOTE' | 'IN_OFFICE',
 *     salaryRangeDown: number,
 *     salaryRangeTop: number,
 *     jobUrl: string,
 *     jobPostSource: string,
 *     createdOn: string,
 *     company: { id: string, name: string, about: string, size: number, profileImageUrl: string },
 *     postedBy: { id: string, fullName: string, profileImageUrl: string, currentPosition: string },
 *     skills: string[],
 *     lynqScore: number | null,
 *   }>,
 *   page: number,
 *   size: number,
 *   totalElements: number,
 *   totalPages: number,
 *   hasNext: boolean,
 *   hasPrevious: boolean,
 * }>} The unwrapped PagedRestResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const get_jobs = async (authFetch, { page = 0, size = 20, filterValue } = {}) => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (filterValue) {
    query.set('filterValue', filterValue);
  }

  // authFetch prepends the app-backend base URL, attaches auth/correlation
  // headers, and unwraps nothing — so read `.data` off the GlobalRestResponse
  // envelope to hand callers the flat PagedRestResponse.
  const payload = await authFetch(`/job?${query}`, { method: 'GET' });
  return payload?.data;
};

/**
 * Fetch a page of the job posts created by the authenticated user, newest first.
 *
 * Calls GET /job/mine (JobController.getMyJobs) through `authFetch`. The backend
 * resolves the owner from the bearer token and returns every job they created —
 * both OPEN and CLOSE — so this powers the owner's "my job posts" management
 * list (unlike get_jobs, which only lists OPEN posts across all companies and
 * hides the caller's own drafts behind the public feed's filters). Same job
 * shape as the feed (GetJobRestResponse), including `jobStatus`.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {object} [params]
 * @param {number} [params.page=0] - Zero-based page index.
 * @param {number} [params.size=20] - Page size.
 * @returns {Promise<object>} The unwrapped PagedRestResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const get_my_jobs = async (authFetch, { page = 0, size = 20 } = {}) => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  const payload = await authFetch(`/job/mine?${query}`, { method: 'GET' });
  return payload?.data;
};

/**
 * Update the editable fields of a job post owned by the authenticated user.
 *
 * Calls PATCH /job/{jobId} (JobController.updateJob) through `authFetch`. Owner-
 * only: the backend resolves the caller from the bearer token and rejects users
 * who did not create the post. The provided skills fully replace the existing
 * ones. `status` (OPEN | CLOSE) is required by the backend, so callers must send
 * the intended status even when it is unchanged. Empty/omitted salary bounds are
 * dropped so they don't fail the backend's @Positive validation.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {string} jobId
 * @param {object} job
 * @param {string} job.title
 * @param {string} job.description
 * @param {'REMOTE' | 'IN_OFFICE'} job.workType
 * @param {'OPEN' | 'CLOSE'} job.status
 * @param {number} [job.salaryRangeDown]
 * @param {number} [job.salaryRangeTop]
 * @param {string[]} [job.skills]
 * @returns {Promise<object>} The unwrapped UpdateJobRestResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const update_job = async (
  authFetch,
  jobId,
  { title, description, workType, status, salaryRangeDown, salaryRangeTop, skills } = {},
) => {
  const body = { title, description, workType, status };
  if (salaryRangeDown != null && salaryRangeDown !== '') {
    body.salaryRangeDown = Number(salaryRangeDown);
  }
  if (salaryRangeTop != null && salaryRangeTop !== '') {
    body.salaryRangeTop = Number(salaryRangeTop);
  }
  body.skills = skills ?? [];

  const payload = await authFetch(`/job/${jobId}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
  return payload?.data;
};

/**
 * Create a job post for the authenticated user's company.
 *
 * Calls POST /job (JobController.createJob) through `authFetch`. The backend
 * derives the company and posting user from the bearer token (and rejects
 * non-company users), so the request body only carries the job's own fields.
 * Posts created here always originate in LYNQ, so `jobPostSource` is fixed to
 * 'LYNQ'. Empty/omitted salary bounds and skills are dropped so they don't fail
 * the backend's @Positive validation.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {object} job
 * @param {string} job.title
 * @param {string} job.description
 * @param {'REMOTE' | 'IN_OFFICE'} job.workType
 * @param {number} [job.salaryRangeDown]
 * @param {number} [job.salaryRangeTop]
 * @param {string[]} [job.skills]
 * @returns {Promise<object>} The unwrapped CreateJobRestResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
/**
 * Ask the backend to AI-generate a list of suggested skills for a job.
 *
 * Calls POST /ml/skill-enhance (LynqMLProxyController.enhanceSkills) through
 * `authFetch`, which proxies to the lynq-ml service to extract key technical
 * skills from the job's title, description and work type — the fields the model
 * reasons over. The caller (create-job form) lets the company owner review and
 * edit the returned skills before they are sent with the actual job post, so
 * this endpoint only proposes; it never persists anything.
 *
 * All three fields are required by the backend (SkillEnhanceRequest: title and
 * description are @NotBlank, workType is @NotNull), and the response is the
 * GlobalRestResponse envelope { success, data: { skills: [] } }.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {object} job
 * @param {string} job.title
 * @param {string} job.description
 * @param {'REMOTE' | 'IN_OFFICE'} job.workType
 * @returns {Promise<string[]>} The suggested skills.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const generate_skills = async (authFetch, { title, description, workType } = {}) => {
  const payload = await authFetch('/ml/skill-enhance', {
    method: 'POST',
    body: JSON.stringify({ title, description, workType }),
  });
  return payload?.data?.skills ?? [];
};

/**
 * Fetch a single job post's full details for the detail page.
 *
 * Calls GET /job/{jobId}/details (JobController.getJobDetails) through
 * `authFetch`. Returns the same job shape as the feed (get_jobs) — including the
 * LYNQ score computed for the authenticated candidate — enriched with two
 * counters: `totalSeen` and `totalCandidatesApplied`. Read-only: it does not
 * count the view (use increase_seen for that).
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {string} jobId
 * @returns {Promise<object>} The unwrapped GetJobDetailForCandidateRestResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const get_job_details = async (authFetch, jobId) => {
  const payload = await authFetch(`/job/${jobId}/details`, { method: 'GET' });
  return payload?.data;
};

/**
 * Increment a job post's "seen" counter.
 *
 * Calls PATCH /job/{jobId}/increase-seen (JobController.increaseSeen) through
 * `authFetch`. Best-effort telemetry fired when a job's detail page is opened —
 * callers should not block the UI on it. Returns the updated counter value.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {string} jobId
 * @returns {Promise<number>} The updated seen counter.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const increase_seen = async (authFetch, jobId) => {
  const payload = await authFetch(`/job/${jobId}/increase-seen`, {
    method: 'PATCH',
  });
  return payload?.data;
};

/**
 * Apply the authenticated user to a job post.
 *
 * Calls POST /job/{jobId}/apply (JobController.applyToJob) through `authFetch`.
 * The applicant is resolved from the bearer token. The backend replies 400 when
 * the user has already applied to the same job, so callers should treat that
 * status as an "already applied" outcome rather than a hard failure.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {string} jobId
 * @returns {Promise<object>} The unwrapped ApplyJobRestResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const apply_to_job = async (authFetch, jobId) => {
  const payload = await authFetch(`/job/${jobId}/apply`, {
    method: 'POST',
  });
  return payload?.data;
};

/**
 * Close a job post so it stops seeking candidates.
 *
 * Calls PATCH /job/{jobId}/close (JobController.closeJob) through `authFetch`.
 * Owner-only: the backend resolves the caller from the bearer token and replies
 * 403 when the authenticated user is not the job's poster, and 400 when the job
 * is not currently OPEN. On success the job transitions OPEN → CLOSE and no
 * longer surfaces in the feed or the details endpoint.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {string} jobId
 * @returns {Promise<object>} The unwrapped CloseJobRestResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const close_job = async (authFetch, jobId) => {
  const payload = await authFetch(`/job/${jobId}/close`, {
    method: 'PATCH',
  });
  return payload?.data;
};

/**
 * Re-open a closed job post so it seeks candidates again.
 *
 * Calls PATCH /job/{jobId}/refresh (JobController.refreshJob) through `authFetch`.
 * Owner-only: the backend resolves the caller from the bearer token and replies
 * 403 when the authenticated user is not the job's poster, and 400 when the job
 * is not currently CLOSE. On success the job transitions CLOSE → OPEN, its
 * created date is reset and it surfaces in the feed again.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {string} jobId
 * @returns {Promise<object>} The unwrapped RefreshJobRestResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const refresh_job = async (authFetch, jobId) => {
  const payload = await authFetch(`/job/${jobId}/refresh`, {
    method: 'PATCH',
  });
  return payload?.data;
};

const create_job = async (
  authFetch,
  { title, description, workType, salaryRangeDown, salaryRangeTop, skills } = {},
) => {
  const body = {
    title,
    description,
    workType,
    jobPostSource: 'LYNQ',
  };
  if (salaryRangeDown != null && salaryRangeDown !== '') {
    body.salaryRangeDown = Number(salaryRangeDown);
  }
  if (salaryRangeTop != null && salaryRangeTop !== '') {
    body.salaryRangeTop = Number(salaryRangeTop);
  }
  if (skills?.length) {
    body.skills = skills;
  }

  const payload = await authFetch('/job', {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return payload?.data;
};

/**
 * Fetch a page of the candidates who applied to a job post, newest application
 * first.
 *
 * Calls GET /job/{jobId}/candidates (JobController.getJobCandidates) through
 * `authFetch`. Powers the owner's "see candidates" list reached from the my-job-
 * posts cards. Each item carries the applicant's public profile plus their LYNQ
 * match score for this job. Note the backend paging param is `pageSize` (not
 * `size`).
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {string} jobId
 * @param {object} [params]
 * @param {number} [params.page=0] - Zero-based page index.
 * @param {number} [params.pageSize=10] - Page size.
 * @returns {Promise<object>} The unwrapped PagedRestResponse of
 *   JobCandidateResponse ({ id, userId, jobId, userFullName, userProfileImage,
 *   userCurrentPosition, userAppliedOn, lynqScore }).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const get_job_candidates = async (authFetch, jobId, { page = 0, pageSize = 10 } = {}) => {
  const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  const payload = await authFetch(`/job/${jobId}/candidates?${query}`, { method: 'GET' });
  return payload?.data;
};

/**
 * Ask the backend for an AI hiring recommendation for one candidate on a job.
 *
 * Calls GET /job/{jobId}/candidate/{candidateId}/candidate-explanation
 * (JobController.explainCandidate) through `authFetch`. Owner-only: the backend
 * resolves the caller from the bearer token, requires them to own both the
 * company and the job post (403 otherwise), reads the job and candidate from the
 * database and forwards the pair to the lynq-ml service. `candidateId` is the
 * applicant's user id (JobCandidateResponse.userId), which the backend looks up
 * in the users table. Replies 404 when the job post or candidate does not exist.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch
 * @param {string} jobId
 * @param {string} candidateId - The applicant's user id (candidate.userId).
 * @returns {Promise<{
 *   recommendation: string,
 *   explanation: string,
 *   strengths: string[],
 *   concerns: string[],
 * }>} The unwrapped CandidateExplanationResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const get_candidate_explanation = async (authFetch, jobId, candidateId) => {
  const payload = await authFetch(
    `/job/${jobId}/candidate/${candidateId}/candidate-explanation`,
    { method: 'GET' },
  );
  return payload?.data;
};

export default {
  get_jobs,
  get_my_jobs,
  update_job,
  get_job_details,
  generate_skills,
  increase_seen,
  apply_to_job,
  close_job,
  refresh_job,
  create_job,
  get_job_candidates,
  get_candidate_explanation,
};
