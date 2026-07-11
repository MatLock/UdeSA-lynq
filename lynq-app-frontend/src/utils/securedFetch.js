// Secured-fetch primitive shared by every authenticated app-backend/IAM call.
//
// A single request that attaches the bearer token + `lynq-request-uuid`
// correlation header, parses the JSON body, and throws an Error carrying
// `status`/`reason` on a non-OK response. It returns the parsed payload (the
// GlobalRestResponse envelope, { success, data }) so callers read `.data`.
//
// Two fetchers are built on top of this:
//  - useApi's `authFetch` — wraps it with refresh-on-401 + retry for in-session
//    pages/components (see useApi.js).
//  - `tokenFetcher` (below) — binds it to a fixed, freshly-minted token for
//    pre-session flows (login, registration, remembered-session bootstrap) where
//    there is no session to refresh from yet.

import requestUuidUtil from './requestUuid';

const APP_BASE_URL =
  import.meta.env.LYNQ_BACKEND_BASE_URL ?? 'http://localhost:8082/lynq-backend-app';

// Perform one secured request. `path` is resolved against the app-backend base
// URL unless it is already absolute (e.g. an IAM endpoint), so this primitive
// serves both services. Extra fetch options are spread through; the auth and
// correlation headers always win.
const sendSecured = async (token, path, options = {}, requestUuid = requestUuidUtil.newRequestUuid()) => {
  const url = path.startsWith('http') ? path : `${APP_BASE_URL}${path}`;

  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'lynq-request-uuid': requestUuid,
      ...options.headers,
      Authorization: `Bearer ${token}`,
    },
  });

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(
      payload?.reason ?? `Request failed with status ${response.status}`,
    );
    error.status = response.status;
    error.reason = payload?.reason;
    throw error;
  }

  return payload;
};

// Build a fixed-token fetcher matching authFetch's `(path, options) => payload`
// shape, for pre-session flows where the token was just minted and there is no
// session to refresh from. An optional shared `requestUuid` lets a multi-call
// flow (e.g. login: IAM auth + profile fetch) trace as one.
const tokenFetcher = (token, requestUuid) => (path, options) =>
  sendSecured(token, path, options, requestUuid);

export default {
  sendSecured,
  tokenFetcher,
};
