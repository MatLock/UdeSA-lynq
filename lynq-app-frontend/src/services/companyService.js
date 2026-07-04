// Company service — talks to the secured app-backend (lynq-backend-app).
// Spec: lynq-app-backend CompanyController.
//
// Mirrors the user profile-image upload flow (see userService): ask the backend
// for a short-lived pre-signed S3 URL (which also persists the object key as the
// company's logo reference), then PUT the image bytes straight to S3.

import requestUuidUtil from '../utils/requestUuid';

const APP_BASE_URL =
  import.meta.env.LYNQ_BACKEND_BASE_URL ?? 'http://localhost:8082/lynq-backend-app';

/**
 * Request a short-lived pre-signed S3 URL to upload the authenticated owner's
 * company logo.
 *
 * Calls GET /company/generate-upload-image?file-name=<fileName>
 * (CompanyController.generateCompanyImageUploadUrl). The endpoint is secured and,
 * as a side effect, persists the S3 object key as the company's logo reference —
 * so the returned URL is a pre-signed HTTP PUT target valid for ~15 minutes. The
 * company owned by the authenticated user must already exist.
 *
 * @param {string} fileName - Name of the file to upload; used to build the S3
 *   object key (e.g. `logo.png`).
 * @param {string} accessToken - Bearer access token.
 * @param {string} [requestUuid] - Correlation id for the `lynq-request-uuid`
 *   header; defaults to a fresh id.
 * @returns {Promise<string>} The pre-signed upload URL (data.preSignedUrl).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const generate_company_image_upload_url = async (
  fileName,
  accessToken,
  requestUuid = requestUuidUtil.newRequestUuid(),
) => {
  const query = new URLSearchParams({ 'file-name': fileName });
  const response = await fetch(`${APP_BASE_URL}/company/generate-upload-image?${query}`, {
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
 * The pre-signed URL already carries the AWS credentials in its query string, so
 * this request must NOT send an Authorization header (it would break the
 * signature). The file is sent as the raw request body.
 *
 * @param {string} preSignedUrl - The pre-signed S3 PUT URL from
 *   {@link generate_company_image_upload_url}.
 * @param {File|Blob} file - The image to upload.
 * @returns {Promise<void>} Resolves once S3 accepts the upload.
 * @throws {Error} On a non-OK S3 response. Carries `status`.
 */
const upload_company_image = async (preSignedUrl, file) => {
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
  generate_company_image_upload_url,
  upload_company_image,
};
