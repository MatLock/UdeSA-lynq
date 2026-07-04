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

/**
 * Update the authenticated user's profile.
 *
 * Calls PATCH /user (UserController.updateUserProfile). The endpoint is secured
 * and partial: only the fields present in the body are modified, so pass `null`
 * for fields that should keep their current value. The user identity is resolved
 * from the bearer token.
 *
 * @param {{
 *   fullName?: string,
 *   currentPosition?: string,
 *   about?: string,
 *   githubUrl?: string,
 *   linkedinUrl?: string,
 *   birthDate?: string,
 * }} profile - Fields to update (UpdateUserProfileRequest shape).
 * @param {string} accessToken - Bearer access token.
 * @param {string} [requestUuid] - Correlation id for the `lynq-request-uuid`
 *   header; defaults to a fresh id.
 * @returns {Promise<object>} The updated profile (unwrapped
 *   UpdateUserProfileRestResponse).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const update_user_profile = async (
  profile,
  accessToken,
  requestUuid = requestUuidUtil.newRequestUuid(),
) => {
  const response = await fetch(`${APP_BASE_URL}/user`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'lynq-request-uuid': requestUuid,
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(profile),
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
  // unwrap so callers receive the flat UpdateUserProfileRestResponse.
  return payload?.data;
};

/**
 * Request a short-lived pre-signed S3 URL to upload the authenticated user's
 * profile image.
 *
 * Calls GET /user/generate-upload-image?file-name=<fileName>
 * (UserController.generateUploadImageUrl). The endpoint is secured and, as a
 * side effect, persists the S3 object key as the user's profile image reference
 * — so the returned URL is a pre-signed HTTP PUT target valid for ~15 minutes.
 *
 * @param {string} fileName - Name of the file to upload; used to build the S3
 *   object key (e.g. `avatar.png`).
 * @param {string} accessToken - Bearer access token from login/register.
 * @param {string} [requestUuid] - Correlation id for the `lynq-request-uuid`
 *   header; defaults to a fresh id.
 * @returns {Promise<string>} The pre-signed upload URL (data.preSignedUrl).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const generate_profile_image_upload_url = async (
  fileName,
  accessToken,
  requestUuid = requestUuidUtil.newRequestUuid(),
) => {
  const query = new URLSearchParams({ 'file-name': fileName });
  const response = await fetch(`${APP_BASE_URL}/user/generate-upload-image?${query}`, {
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
  // unwrap so callers receive the flat GenerateUploadImageRestResponse.
  return payload?.data?.preSignedUrl;
};

/**
 * Upload an image binary directly to S3 using a pre-signed PUT URL.
 *
 * The pre-signed URL already carries the AWS credentials in its query string,
 * so this request must NOT send an Authorization header (it would break the
 * signature). The file is sent as the raw request body.
 *
 * @param {string} preSignedUrl - The pre-signed S3 PUT URL from
 *   {@link generate_profile_image_upload_url}.
 * @param {File|Blob} file - The image to upload.
 * @returns {Promise<void>} Resolves once S3 accepts the upload.
 * @throws {Error} On a non-OK S3 response. Carries `status`.
 */
const upload_profile_image = async (preSignedUrl, file) => {
  const response = await fetch(preSignedUrl, {
    method: 'PUT',
    headers: {
      'Content-Type': file.type || 'application/octet-stream',
    },
    body: file,
  });

  if (!response.ok) {
    const error = new Error(`Image upload failed with status ${response.status}`);
    error.status = response.status;
    throw error;
  }
};

export default {
  get_user,
  update_user_profile,
  generate_profile_image_upload_url,
  upload_profile_image,
};
