// Company service — talks to the secured app-backend (lynq-backend-app).
// Spec: lynq-app-backend CompanyController.
//
// Mirrors the user profile-image upload flow (see userService): ask the backend
// to register the logo in lynq-file-storage (which also persists the file id as
// the company's logo reference), PUT the image bytes straight to the returned
// pre-signed URL, then confirm the upload.


/**
 * Register the authenticated owner's new company logo and get a short-lived
 * pre-signed URL to upload it to. Once the PUT succeeds, the upload must be
 * confirmed with {@link confirm_company_image_upload} using the returned
 * `fileId`, otherwise the file stays PENDING in lynq-file-storage.
 *
 * Calls GET /company/generate-upload-image?file-name=<fileName>
 * (CompanyController.generateCompanyImageUploadUrl). The endpoint is secured and,
 * as a side effect, registers the file in lynq-file-storage and persists its id
 * as the company's logo reference — so the returned URL is a pre-signed HTTP PUT
 * target valid for ~15 minutes. The company owned by the authenticated user must
 * already exist.
 *
 * Takes an `authFetch`-shaped fetcher rather than a raw token (mirrors
 * userService): in-session callers pass useApi's authFetch (auto-refreshes an
 * expired token), while pre-session flows (registration) pass
 * securedFetch.tokenFetcher(token).
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher.
 * @param {string} fileName - Name of the file to upload; lynq-file-storage uses
 *   it to build the object key (e.g. `logo.png`).
 * @returns {Promise<{preSignedUrl: string, fileId: string}>} The upload target
 *   and the id of the registered file.
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
  // unwrap so callers receive the flat GenerateUploadImageRestResponse
  // ({ preSignedUrl, fileId }).
  return payload?.data;
};

/**
 * Confirm the logo upload finished, which marks the file available in
 * lynq-file-storage.
 *
 * Calls POST /company/confirm-upload-image?file-id=<fileId>
 * (CompanyController.confirmCompanyImageUpload) after the pre-signed PUT
 * succeeded. The file id must be the one currently registered for the company.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher (useApi's authFetch).
 * @param {string} fileId - Id returned by
 *   {@link generate_company_image_upload_url}.
 * @returns {Promise<void>} Resolves once the file is marked available.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const confirm_company_image_upload = async (authFetch, fileId) => {
  const query = new URLSearchParams({ 'file-id': fileId });
  await authFetch(`/company/confirm-upload-image?${query}`, {
    method: 'POST',
  });
};

/**
 * Upload an image binary directly to the storage bucket using a pre-signed PUT
 * URL issued by lynq-file-storage.
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
  confirm_company_image_upload,
};
