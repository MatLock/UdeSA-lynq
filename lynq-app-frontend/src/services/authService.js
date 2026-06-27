// Auth service — talks to the lynq-iam API.
// Spec: lynq-iam/iam_openapi.yaml

const IAM_BASE_URL =
  import.meta.env.VITE_IAM_BASE_URL ?? 'http://localhost:8080/lynq-iam';

/**
 * Shared login request against the lynq-iam auth endpoints. All login endpoints
 * accept a JSON body, require the `lynq-request-uuid` correlation header, and
 * return the same UserRestResponse payload, so this centralizes that contract.
 *
 * @param {string} path - Endpoint path relative to the IAM base URL.
 * @param {object} body - JSON request body for the endpoint.
 * @returns {Promise<object>} The parsed UserRestResponse payload.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const login = async (path, body) => {
  const response = await fetch(`${IAM_BASE_URL}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'lynq-request-uuid': crypto.randomUUID(),
    },
    body: JSON.stringify(body),
  });

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(
      payload?.reason ?? `Login failed with status ${response.status}`
    );
    error.status = response.status;
    error.reason = payload?.reason;
    throw error;
  }

  return payload;
}

/**
 * Authenticate a user with their username and password.
 *
 * Calls POST /auth/login/username (operationId: loginByUsername).
 *
 * @param {string} username - Unique username (3–20 chars).
 * @param {string} password - User password (min 8 chars).
 * @returns {Promise<{
 *   id: string,
 *   username: string,
 *   email: string,
 *   creationDate: string,
 *   accessToken: string,
 *   refreshToken: string,
 * }>} The authenticated user with access and refresh tokens.
 * @throws {Error} If credentials are invalid or the request fails. The thrown
 *   error carries `status` (HTTP code) and `reason` (server-provided message).
 */
const user_authenticate = async (username, password) =>
  login('/auth/login/username', { username, password });

/**
 * Authenticate a user with their email and password.
 *
 * Calls POST /auth/login/email (operationId: loginByEmail).
 *
 * @param {string} email - Unique email address (max 100 chars).
 * @param {string} password - User password (min 8 chars).
 * @returns {Promise<{
 *   id: string,
 *   username: string,
 *   email: string,
 *   creationDate: string,
 *   accessToken: string,
 *   refreshToken: string,
 * }>} The authenticated user with access and refresh tokens.
 * @throws {Error} If credentials are invalid or the request fails. The thrown
 *   error carries `status` (HTTP code) and `reason` (server-provided message).
 */
const email_authenticate = async (email, password) =>
  login('/auth/login/email', { email, password });

/**
 * Register a new user.
 *
 * Calls POST /auth/register (operationId: createUser).
 *
 * @param {object} userInfo - New user details (CreateUserRequest).
 * @param {string} userInfo.username - Unique username (3–20 chars).
 * @param {string} userInfo.password - User password (min 8 chars).
 * @param {string} userInfo.email - Unique email address (max 100 chars).
 * @returns {Promise<{
 *   id: string,
 *   username: string,
 *   email: string,
 *   creationDate: string,
 *   accessToken: string,
 *   refreshToken: string,
 * }>} The newly created user with access and refresh tokens.
 * @throws {Error} On invalid fields (400) or duplicate username/email (409).
 *   The thrown error carries `status` (HTTP code) and `reason` (server message).
 */
const user_register = async (userInfo) => {
  const response = await fetch(`${IAM_BASE_URL}/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'lynq-request-uuid': crypto.randomUUID(),
    },
    body: JSON.stringify(userInfo),
  });

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(
      payload?.reason ?? `Registration failed with status ${response.status}`
    );
    error.status = response.status;
    error.reason = payload?.reason;
    throw error;
  }

  return payload;
}

/**
 * Update the authenticated user's password.
 *
 * Calls PATCH /auth/update-password (operationId: updatePassword). The endpoint
 * is secured, so a valid access token is required; on success it returns the
 * user with a freshly generated access and refresh token.
 *
 * @param {string} newPassword - The new password (min 8 chars).
 * @param {string} accessToken - The current valid access token (Bearer auth).
 * @returns {Promise<{
 *   id: string,
 *   username: string,
 *   email: string,
 *   creationDate: string,
 *   accessToken: string,
 *   refreshToken: string,
 * }>} The user with the newly generated access and refresh tokens.
 * @throws {Error} On invalid fields (400), missing/invalid token (401), or user
 *   not found (403). The thrown error carries `status` and `reason`.
 */
const user_update_password = async (newPassword, accessToken) => {
  const response = await fetch(`${IAM_BASE_URL}/auth/update-password`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'lynq-request-uuid': crypto.randomUUID(),
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ newPassword }),
  });

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(
      payload?.reason ?? `Password update failed with status ${response.status}`
    );
    error.status = response.status;
    error.reason = payload?.reason;
    throw error;
  }

  return payload;
}

/**
 * Generate a new access token from a valid refresh token.
 *
 * Calls POST /auth/refresh (operationId: generateNewAccessToken). The endpoint
 * is secured: the refresh token is sent as the Bearer credential, and there is
 * no request body.
 *
 * @param {string} refresh_token - A valid refresh token (Bearer auth).
 * @returns {Promise<string>} The newly generated access token.
 * @throws {Error} On a missing Authorization header (401) or an invalid/expired
 *   refresh token (403). The thrown error carries `status` and `reason`.
 */
const refresh_access_token = async (refresh_token) => {
  const response = await fetch(`${IAM_BASE_URL}/auth/refresh`, {
    method: 'POST',
    headers: {
      'lynq-request-uuid': crypto.randomUUID(),
      Authorization: `Bearer ${refresh_token}`,
    },
  });

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(
      payload?.reason ?? `Token refresh failed with status ${response.status}`
    );
    error.status = response.status;
    error.reason = payload?.reason;
    throw error;
  }

  return payload?.accessToken;
}

/**
 * Check whether an access token is valid and not expired.
 *
 * Calls GET /auth/validate (operationId: isAccessTokenValid). The token to
 * check is sent as the Bearer credential.
 *
 * @param {string} accessToken - The access token to validate (Bearer auth).
 * @returns {Promise<boolean>} True if the token is valid, false otherwise.
 * @throws {Error} On a missing Authorization header (401). The thrown error
 *   carries `status` and `reason`.
 */
const validate_access_token = async (accessToken) => {
  const response = await fetch(`${IAM_BASE_URL}/auth/validate`, {
    method: 'GET',
    headers: {
      'lynq-request-uuid': crypto.randomUUID(),
      Authorization: `Bearer ${accessToken}`,
    },
  });

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(
      payload?.reason ??
        `Token validation failed with status ${response.status}`
    );
    error.status = response.status;
    error.reason = payload?.reason;
    throw error;
  }
  return Boolean(payload?.data);
}

/**
 * Extract the user identity carried by a valid access token.
 *
 * Calls GET /auth/user-info (operationId: obtainUserInfoFromToken). The token
 * is sent as the Bearer credential.
 *
 * @param {string} accessToken - A valid access token (Bearer auth).
 * @returns {Promise<{ id: string, username: string, email: string }>} The user
 *   identity extracted from the token (UserInfoRestResponse).
 * @throws {Error} On a missing or invalid access token (401). The thrown error
 *   carries `status` and `reason`.
 */
const user_info = async (accessToken) => {
  const response = await fetch(`${IAM_BASE_URL}/auth/user-info`, {
    method: 'GET',
    headers: {
      'lynq-request-uuid': crypto.randomUUID(),
      Authorization: `Bearer ${accessToken}`,
    },
  });

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(
      payload?.reason ?? `Fetching user info failed with status ${response.status}`
    );
    error.status = response.status;
    error.reason = payload?.reason;
    throw error;
  }

  return payload;
}

export default {
  user_authenticate,
  email_authenticate,
  user_register,
  user_update_password,
  refresh_access_token,
  validate_access_token,
  user_info,
};
