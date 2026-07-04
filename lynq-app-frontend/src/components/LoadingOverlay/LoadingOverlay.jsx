import Spinner from '../Spinner/Spinner'
import './LoadingOverlay.css'

// Full-screen blocking overlay: dims and covers the whole viewport with a
// centered spinner and a caption, so the user can't interact while a critical
// task (e.g. uploading a profile picture) is in flight.
const LoadingOverlay = ({ label }) => (
  <div className="loading-overlay" role="alert" aria-busy="true" aria-live="assertive">
    <div className="loading-overlay-card">
      <Spinner label={label} />
    </div>
  </div>
)

export default LoadingOverlay
