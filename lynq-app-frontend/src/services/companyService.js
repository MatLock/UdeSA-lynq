// Company service — talks to the secured app-backend (lynq-backend-app).
// Spec: lynq-app-backend CompanyController.
//
// Mirrors the user profile-image upload flow (see userService): ask the backend
// for a short-lived pre-signed S3 URL (which also persists the object key as the
// company's logo reference), then PUT the image bytes straight to S3.


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
 * Takes an `authFetch`-shaped fetcher rather than a raw token (mirrors
 * userService): in-session callers pass useApi's authFetch (auto-refreshes an
 * expired token), while pre-session flows (registration) pass
 * securedFetch.tokenFetcher(token).
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher.
 * @param {string} fileName - Name of the file to upload; used to build the S3
 *   object key (e.g. `logo.png`).
 * @returns {Promise<string>} The pre-signed upload URL (data.preSignedUrl).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
/**
 * Fetch a company's public detail by id.
 *
 * Calls GET /company/{companyId} (CompanyController.getCompanyDetail) through the
 * caller's `authFetch` (see useApi), which injects the bearer token and refreshes
 * an expired one. Used by the company detail page reached from a job's "View
 * company" link. The payload carries the company's profile plus the jobs it has
 * posted.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   authenticated fetcher from useApi.
 * @param {string} companyId
 * @returns {Promise<{
 *   id: string,
 *   name: string,
 *   about: string,
 *   size: number,
 *   profileImageUrl: string,
 *   createdOn: string,
 *   jobs: Array<{ id: string, title: string, description: string }>,
 * }>} The unwrapped GetCompanyDetailRestResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const get_company_detail = async (authFetch, companyId) => {
  const payload = await authFetch(`/company/${companyId}`, { method: 'GET' });
  // Unwrap the GlobalRestResponse envelope ({ success, data }).
  return payload?.data;
};

/**
 * Update the authenticated owner's company.
 *
 * Calls PATCH /company (CompanyController.updateCompany) through the caller's
 * `authFetch` (see useApi). Partial: only the fields present in the body are
 * modified, so pass `null` for fields that should keep their current value. The
 * company is resolved server-side from the bearer token (the owner's own
 * company). The logo is NOT sent here — it uploads separately via the pre-signed
 * URL flow ({@link generate_company_image_upload_url} + {@link upload_company_image}).
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   authenticated fetcher from useApi.
 * @param {{ name?: string, about?: string, size?: number }} company - Fields to
 *   update (UpdateCompanyRequest shape).
 * @returns {Promise<{
 *   id: string,
 *   name: string,
 *   about: string,
 *   size: number,
 *   profileImageUrl: string,
 *   createdOn: string,
 * }>} The updated company (unwrapped UpdateCompanyRestResponse).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const update_company = async (authFetch, company) => {
  const payload = await authFetch('/company', {
    method: 'PATCH',
    body: JSON.stringify(company),
  });
  // Unwrap the GlobalRestResponse envelope ({ success, data }).
  return payload?.data;
};

const generate_company_image_upload_url = async (authFetch, fileName) => {
  const query = new URLSearchParams({ 'file-name': fileName });
  const payload = await authFetch(`/company/generate-upload-image?${query}`, {
    method: 'GET',
  });
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
  get_company_detail,
  update_company,
  generate_company_image_upload_url,
  upload_company_image,
};
