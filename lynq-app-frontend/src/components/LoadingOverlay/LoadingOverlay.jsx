import { createPortal } from 'react-dom'
import Spinner from '../Spinner/Spinner'
import './LoadingOverlay.css'

// Full-screen blocking overlay: dims and covers the whole viewport with a
// centered spinner and a caption, so the user can't interact while a critical
// task (e.g. uploading a profile picture) is in flight.
//
// Portaled to <body> for the same reason Toast is: a `position: fixed` element
// inside a transformed, overflow-hidden ancestor (the register/resume carousel
// track) is positioned against that ancestor and clipped by it — so rendered in
// place it would cover only the active slide and block nothing.
const LoadingOverlay = ({ label }) =>
  createPortal(
    <div className="loading-overlay" role="alert" aria-busy="true" aria-live="assertive">
      <div className="loading-overlay-card">
        <Spinner label={label} />
      </div>
    </div>,
    document.body,
  )

export default LoadingOverlay
