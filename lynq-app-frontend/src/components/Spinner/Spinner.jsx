import './Spinner.css'

// Brand-colored spinning ring with an optional caption. Shared by the blocking
// LoadingOverlay and inline page loaders so the loading look stays consistent.
const Spinner = ({ label }) => (
  <div className="spinner-stack" role="status" aria-live="polite">
    <span className="spinner" aria-hidden="true" />
    {label && <p className="spinner-label">{label}</p>}
  </div>
)

export default Spinner
