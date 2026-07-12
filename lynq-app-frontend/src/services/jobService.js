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

export default {
  get_jobs,
  generate_skills,
  create_job,
};
