// Fallback logo glyph — a simple building/office silhouette. Shown on a job
// card when the company has no profile image, or for external (scraped) posts
// that carry no trusted company logo.
const CompanyIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path
      d="M4 20V6a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v14M13 20V10a1 1 0 0 1 1-1h5a1 1 0 0 1 1 1v10M3 20h18"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      fill="none"
    />
    <path
      d="M7 9h2M7 12h2M7 15h2M16 12h1M16 15h1"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    />
  </svg>
)

export default CompanyIcon
