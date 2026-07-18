// User service — talks to the secured app-backend (lynq-backend-app).
// Spec: lynq-app-backend/openapi.yaml (UserController).
//
// The secured calls take an `authFetch`-shaped fetcher ((path, options) =>
// Promise<payload>) rather than a raw token: in-session pages pass useApi's
// authFetch (auto-refreshes an expired token), while pre-session flows (login,
// register, remembered-session bootstrap) pass securedFetch.tokenFetcher(token).

/**
 * Fetch the authenticated user's profile.
 *
 * Calls GET /user (UserController.getUser).
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher (useApi's authFetch, or a tokenFetcher for pre-session use).
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
const get_user = async (authFetch) => {
  const payload = await authFetch('/user', { method: 'GET' });
  // Unwrap the GlobalRestResponse envelope ({ success, data }).
  return payload?.data;
};

/**
 * Fetch another user's public profile by id.
 *
 * Calls GET /user/{userId} (UserController.getUserProfile). Used by the public
 * user profile page reached from a job's poster/recruiter link. For COMPANY
 * users the payload also carries their `company` and the `jobs` they have posted;
 * for candidates those are omitted.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher (useApi's authFetch).
 * @param {string} userId
 * @returns {Promise<{
 *   fullName: string,
 *   profileImageUrl: string,
 *   currentPosition: string,
 *   about: string,
 *   githubUrl: string,
 *   linkedinUrl: string,
 *   company: { name: string, profileImageUrl: string } | null,
 *   jobs: Array<{ id: string, title: string, description: string }> | null,
 * }>} The unwrapped GetUserProfileRestResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const get_user_profile = async (authFetch, userId) => {
  const payload = await authFetch(`/user/${userId}`, { method: 'GET' });
  // Unwrap the GlobalRestResponse envelope ({ success, data }).
  return payload?.data;
};

/**
 * Update the authenticated user's profile.
 *
 * Calls PATCH /user (UserController.updateUserProfile). Partial: only the fields
 * present in the body are modified, so pass `null` for fields that should keep
 * their current value. The user identity is resolved from the bearer token.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher (useApi's authFetch).
 * @param {{
 *   fullName?: string,
 *   currentPosition?: string,
 *   about?: string,
 *   githubUrl?: string,
 *   linkedinUrl?: string,
 *   birthDate?: string,
 * }} profile - Fields to update (UpdateUserProfileRequest shape).
 * @returns {Promise<object>} The updated profile (unwrapped
 *   UpdateUserProfileRestResponse).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const update_user_profile = async (authFetch, profile) => {
  const payload = await authFetch('/user', {
    method: 'PATCH',
    body: JSON.stringify(profile),
  });
  // Unwrap the GlobalRestResponse envelope ({ success, data }).
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
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher (useApi's authFetch).
 * @param {string} fileName - Name of the file to upload; used to build the S3
 *   object key (e.g. `avatar.png`).
 * @returns {Promise<string>} The pre-signed upload URL (data.preSignedUrl).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const generate_profile_image_upload_url = async (authFetch, fileName) => {
  const query = new URLSearchParams({ 'file-name': fileName });
  const payload = await authFetch(`/user/generate-upload-image?${query}`, {
    method: 'GET',
  });
  // Unwrap the GlobalRestResponse envelope ({ success, data }).
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
  get_user_profile,
  update_user_profile,
  generate_profile_image_upload_url,
  upload_profile_image,
};
