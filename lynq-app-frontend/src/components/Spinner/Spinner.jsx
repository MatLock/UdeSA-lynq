import './Spinner.css'

// Brand-colored spinning ring with an optional caption. Shared by the blocking
// LoadingOverlay and inline page loaders so the loading look stays consistent.
//
// The caption is keyed by its own text so a changing label (a caller rotating
// through phrases during a long wait — see hooks/useRotatingPhrase) remounts just
// the paragraph and replays its fade-in, while the ring keeps spinning unbroken.
const Spinner = ({ label }) => (
  <div className="spinner-stack" role="status" aria-live="polite">
    <span className="spinner" aria-hidden="true" />
    {label && (
      <p key={label} className="spinner-label">
        {label}
      </p>
    )}
  </div>
)

export default Spinner
