// User service — talks to the secured app-backend (lynq-backend-app).
// Spec: lynq-app-backend/openapi.yaml (UserController).

import requestUuidUtil from '../utils/requestUuid';

const APP_BASE_URL =
  import.meta.env.LYNQ_BACKEND_BASE_URL ?? 'http://localhost:8082/lynq-backend-app';

/**
 * Fetch the authenticated user's profile.
 *
 * Calls GET /user (UserController.getUser). The endpoint is secured, so it needs
 * the bearer access token and the `lynq-request-uuid` correlation header.
 *
 * @param {string} accessToken - Bearer access token from login/register.
 * @param {string} [requestUuid] - Correlation id for the `lynq-request-uuid`
 *   header; defaults to a fresh id. Pass a shared id to trace a multi-call
 *   functionality across services.
 * @returns {Promise<{
 *   id: string,
 *   userType: string,
 *   fullName: string,
 *   userProfileImageUrl: string,
 *   currentPosition: string,
 *   about: string,
 *   githubUrl: string,
 *   linkedinUrl: string,
 *   birthDate: string,
 *   createdOn: string,
 * }>} The user profile (the unwrapped GetUserRestResponse).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const get_user = async (accessToken, requestUuid = requestUuidUtil.newRequestUuid()) => {
  const response = await fetch(`${APP_BASE_URL}/user`, {
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
  // unwrap so callers receive the flat GetUserRestResponse.
  return payload?.data;
};

export default {
  get_user,
};
