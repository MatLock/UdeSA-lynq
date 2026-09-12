// The single origin the browser talks to: lynq-bff.
//
// Every call leaves through the gateway — the app-backend resources, the ML
// features, the files, and the lynq-iam auth endpoints it relays. lynq-iam has
// no public route of its own, so there is one base URL here rather than one per
// service, and `LYNQ_BFF_BASE_URL` is the only one Vite has to bake in.

const APP_BASE_URL =
  import.meta.env.LYNQ_BFF_BASE_URL ?? 'http://localhost:8087/lynq-bff';

// Absolute URL for a gateway path (e.g. '/auth/login/email').
const url = (path) => `${APP_BASE_URL}${path}`;

export default {
  APP_BASE_URL,
  url,
};
