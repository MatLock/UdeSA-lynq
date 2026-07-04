// Job service — talks to the secured app-backend (lynq-backend-app).
// Spec: lynq-app-backend JobController (GET /job).

import requestUuidUtil from '../utils/requestUuid';

const APP_BASE_URL =
  import.meta.env.LYNQ_BACKEND_BASE_URL ?? 'http://localhost:8082/lynq-backend-app';

/**
 * Fetch a page of available job posts, newest first.
 *
 * Calls GET /job (JobController.getJobs). The endpoint is secured, so it needs
 * the bearer access token and the `lynq-request-uuid` correlation header. A
 * single free-text `filterValue` is matched (case-insensitive, contains) across
 * the job title, description, company name, work type and skills; omit it to
 * list everything.
 *
 * @param {string} accessToken - Bearer access token from login/register.
 * @param {object} [params] - Query parameters.
 * @param {number} [params.page=0] - Zero-based page index.
 * @param {number} [params.size=20] - Page size.
 * @param {string} [params.filterValue] - Free-text search; omitted when blank.
 * @param {string} [requestUuid] - Correlation id for the `lynq-request-uuid`
 *   header; defaults to a fresh id.
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
const get_jobs = async (
  accessToken,
  { page = 0, size = 20, filterValue } = {},
  requestUuid = requestUuidUtil.newRequestUuid(),
) => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (filterValue) {
    query.set('filterValue', filterValue);
  }

  const response = await fetch(`${APP_BASE_URL}/job?${query}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'lynq-request-uuid': requestUuid,
      Authorization: `Bearer ${accessToken}`,
    },
  });

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(
      payload?.reason ?? `Request failed with status ${response.status}`
    );
    error.status = response.status;
    error.reason = payload?.reason;
    throw error;
  }

  // Success responses wrap the payload in a GlobalRestResponse ({ success, data });
  // unwrap so callers receive the flat PagedRestResponse.
  return payload?.data;
};

export default {
  get_jobs,
};
