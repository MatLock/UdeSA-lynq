// Fallback avatar glyph — a head-and-shoulders silhouette. Shared by the
// sidebar header avatar and the profile page avatar so both show the same
// icon when the user has no profile image set.
const UserIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <circle cx="12" cy="8" r="4" fill="currentColor" />
    <path
      d="M4 20c0-4 3.6-6 8-6s8 2 8 6"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      fill="none"
    />
  </svg>
)

export default UserIcon
