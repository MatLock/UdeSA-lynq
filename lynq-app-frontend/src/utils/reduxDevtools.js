// Dev-only bridge to the Redux DevTools browser extension.
//
// The app uses React Context, not Redux — but the extension exposes a generic
// `window.__REDUX_DEVTOOLS_EXTENSION__.connect()` API that accepts arbitrary
// state snapshots. We push our context slices through it so they're inspectable
// in the DevTools timeline. No-op in production builds and when the extension
// (or window) is absent, so it's safe to call unconditionally.
//
// Requires the "Redux DevTools" browser extension to be installed.

const isEnabled = () =>
  import.meta.env.DEV &&
  typeof window !== 'undefined' &&
  Boolean(window.__REDUX_DEVTOOLS_EXTENSION__);

// One shared connection so every slice lands in the same "LYNQ" instance.
let connection = null;

function getConnection() {
  if (!isEnabled()) return null;
  if (!connection) {
    connection = window.__REDUX_DEVTOOLS_EXTENSION__.connect({ name: 'LYNQ' });
    connection.init({});
  }
  return connection;
}

// Record a named state change; it appears as an action in the DevTools timeline.
function send(action, state) {
  const conn = getConnection();
  if (!conn) return;
  conn.send(action, state);
}

export default {
  isEnabled,
  send,
};
