// Local cache for a user's profile image, keyed by user id.
//
// The backend serves the profile image through a pre-signed S3 URL that expires
// after 15 minutes, so it can't be relied on for long-lived display. When the
// user uploads a new picture we stash the image bytes here (as a base64 data
// URL) so the avatar keeps rendering across reloads without a valid pre-signed
// URL. Values live in localStorage so they survive a full browser restart.

const KEY_PREFIX = 'lynq.profileImage.';

const keyFor = (userId) => `${KEY_PREFIX}${userId}`;

// Return the cached data URL for the user, or null when nothing is cached.
const read = (userId) => {
  if (!userId) return null;
  try {
    return localStorage.getItem(keyFor(userId));
  } catch {
    return null;
  }
};

// Cache the given data URL as the user's profile image.
const write = (userId, dataUrl) => {
  if (!userId || !dataUrl) return;
  try {
    localStorage.setItem(keyFor(userId), dataUrl);
  } catch {
    // Storage full or unavailable — caching is best-effort, so ignore.
  }
};

// Drop the cached image for the user.
const remove = (userId) => {
  if (!userId) return;
  try {
    localStorage.removeItem(keyFor(userId));
  } catch {
    // Nothing to do if storage is unavailable.
  }
};

export default {
  read,
  write,
  remove,
};
