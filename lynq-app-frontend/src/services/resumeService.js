// Resume service — talks to the secured app-backend (lynq-backend-app).
// Spec: lynq-app-backend/openapi.yaml (UserController resume operations).
//
// Like userService, the secured calls take an `authFetch`-shaped fetcher
// ((path, options) => Promise<payload>) rather than a raw token, so the caller
// decides whether an expired access token is refreshed (useApi's authFetch) or
// not (securedFetch.tokenFetcher).

// Language codes the backend stores a resume under (com.lynq.backend.enums.Language).
const LANGUAGES = ['EN', 'ES', 'FR', 'PR'];

/**
 * Fetch every resume of the authenticated candidate.
 *
 * Calls GET /user/resume (UserController.getUserResumes). Each entry carries the
 * structured resume JSON plus a short-lived public link to the PDF stored in S3.
 * Candidate-only: the backend rejects other user types with 400.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher (useApi's authFetch).
 * @returns {Promise<Array<{
 *   id: string,
 *   name: string | null,
 *   language: string,
 *   createdOn: string,
 *   resume: object | null,
 *   pdfUrl: string | null,
 * }>>} The unwrapped list of GetUserResumeRestResponse. Empty when the candidate
 *   has no resume yet — the signal to start the creation workflow.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const get_resumes = async (authFetch) => {
  const payload = await authFetch('/user/resume', { method: 'GET' });
  // Unwrap the GlobalRestResponse envelope ({ success, data }).
  return payload?.data ?? [];
};

/**
 * Request a short-lived pre-signed S3 URL to upload the authenticated
 * candidate's resume document.
 *
 * Calls GET /user/generate-upload-resume?file-name=<fileName>
 * (UserController.generateUploadResumeUrl). The returned URL is a pre-signed
 * HTTP PUT target; the document bytes go straight to S3, never through the
 * backend. Candidate-only.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher (useApi's authFetch).
 * @param {string} fileName - Name of the file to upload; used to build the S3
 *   object key (e.g. `resume.pdf`).
 * @returns {Promise<{ preSignedUrl: string, fileId: string }>} The pre-signed
 *   upload URL and the file id it was registered under.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const generate_resume_upload_url = async (authFetch, fileName) => {
  const query = new URLSearchParams({ 'file-name': fileName });
  const payload = await authFetch(`/user/generate-upload-resume?${query}`, {
    method: 'GET',
  });
  // Unwrap the GlobalRestResponse envelope ({ success, data }).
  return payload?.data;
};

/**
 * Upload a resume document directly to S3 using a pre-signed PUT URL.
 *
 * The pre-signed URL already carries the AWS credentials in its query string, so
 * this request must NOT send an Authorization header (it would break the
 * signature). The file is sent as the raw request body.
 *
 * @param {string} preSignedUrl - The pre-signed S3 PUT URL from
 *   {@link generate_resume_upload_url}.
 * @param {File|Blob} file - The PDF/DOC/DOCX to upload.
 * @returns {Promise<void>} Resolves once S3 accepts the upload.
 * @throws {Error} On a non-OK S3 response. Carries `status`.
 */
const upload_resume = async (preSignedUrl, file) => {
  const response = await fetch(preSignedUrl, {
    method: 'PUT',
    headers: {
      'Content-Type': file.type || 'application/octet-stream',
    },
    body: file,
  });

  if (!response.ok) {
    const error = new Error(`Resume upload failed with status ${response.status}`);
    error.status = response.status;
    throw error;
  }
};

const import_resume_document = async (authFetch, fileId, language) => {
  const query = language ? `?${new URLSearchParams({ language })}` : '';
  const payload = await authFetch(
    `/resume/document/${encodeURIComponent(fileId)}/import${query}`,
    { method: 'POST' },
  );
  return payload?.data;
};

const preview_resume = async (authFetch, body) => {
  const payload = await authFetch('/resume/preview', {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return payload?.data;
};

const delete_resume_preview = async (authFetch, fileId) => {
  await authFetch(`/resume/preview/${encodeURIComponent(fileId)}`, {
    method: 'DELETE',
  });
};

/**
 * Delete a resume the candidate had already created.
 *
 * Calls DELETE /resume/{resumeId} on lynq-bff, which removes the resume from the
 * app-backend and then drops its PDF from lynq-file-storage — the two live in
 * different services and only the gateway talks to both. The candidate is
 * resolved from the bearer token, so a resume that is not theirs answers 404.
 *
 * The candidate's skills are deliberately kept: they are merged from every
 * resume the person wrote, so there is no way to tell which came from this one,
 * and dropping them would silently lower their LyNQ score.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher (useApi's authFetch).
 * @param {string} resumeId - Id of the resume to delete.
 * @returns {Promise<void>} Resolves once the resume is gone (204).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const delete_resume = async (authFetch, resumeId) => {
  await authFetch(`/resume/${encodeURIComponent(resumeId)}`, {
    method: 'DELETE',
  });
};

/**
 * Persist a resume the candidate filled in through the creation wizard.
 *
 * Calls POST /user/resume with the structured resume JSON. The candidate is
 * resolved from the bearer token, so only the resume itself is sent.
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher (useApi's authFetch).
 * @param {{ name: string, language: string, resume: object, fileId: string }} body - The resume
 *   name, the language its content is written in (see {@link LANGUAGES}), the
 *   resume JSON itself, and the file id of its PDF.
 * @returns {Promise<object>} The created resume (unwrapped
 *   GetUserResumeRestResponse).
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const create_resume = async (authFetch, body) => {
  const payload = await authFetch('/user/resume', {
    method: 'POST',
    body: JSON.stringify(body),
  });
  // Unwrap the GlobalRestResponse envelope ({ success, data }).
  return payload?.data;
};

/**
 * Ask the backend to AI-extract the skills implied by a resume.
 *
 * Calls POST /resume/skill-extraction, which lynq-bff relays to lynq-ml, which
 * reads the structured resume and returns its skills consolidated into three
 * buckets. The model reads the whole resume — experience, education, projects —
 * so the payload is the same JSON shape a resume is stored in, even while it is
 * still a draft.
 * Candidate-only; nothing is persisted, the caller decides what to keep.
 *
 * Note the response buckets are named `skills`/`tools`/`soft`, where `skills` is
 * the technical bucket (resume JSON's `skills.technical`).
 *
 * @param {(path: string, options?: object) => Promise<object>} authFetch - The
 *   secured fetcher (useApi's authFetch).
 * @param {object} resume - The resume JSON to reason over.
 * @param {string} [language] - The caller's UI language code (e.g. `es`),
 *   forwarded so lynq-ml writes the soft skills in it. Technical skills and tool
 *   names are never translated. Without it the model infers a language from the
 *   resume text, which need not be the one the candidate is working in; lynq-ml
 *   defaults to English when omitted.
 * @returns {Promise<{ skills: string[], tools: string[], soft: string[] }>} The
 *   unwrapped SkillExtractionResponse.
 * @throws {Error} On a non-OK response. Carries `status` and `reason`.
 */
const extract_skills = async (authFetch, resume, language) => {
  const query = language ? `?${new URLSearchParams({ language })}` : '';
  const payload = await authFetch(`/resume/skill-extraction${query}`, {
    method: 'POST',
    body: JSON.stringify(resume),
  });
  // Unwrap the GlobalRestResponse envelope ({ success, data }).
  return payload?.data;
};

export default {
  LANGUAGES,
  get_resumes,
  generate_resume_upload_url,
  upload_resume,
  import_resume_document,
  preview_resume,
  create_resume,
  delete_resume,
  delete_resume_preview,
  extract_skills,
};
