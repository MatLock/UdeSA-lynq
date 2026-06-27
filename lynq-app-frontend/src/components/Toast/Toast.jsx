import { useEffect } from 'react'
import strings from '../../i18n'
import './Toast.css'

const Toast = ({
  message,
  type = 'error',
  duration = 4000,
  onClose,
  closeLabel = strings.common.close,
}) => {
  useEffect(() => {
    if (!message || !duration) return
    const timer = setTimeout(() => onClose?.(), duration)
    return () => clearTimeout(timer)
  }, [message, duration, onClose])

  if (!message) return null

  return (
    <div className={`toast toast-${type}`} role="alert">
      <span className="toast-message">{message}</span>
      <button
        type="button"
        className="toast-close"
        aria-label={closeLabel}
        onClick={() => onClose?.()}
      >
        ×
      </button>
    </div>
  )
}

export default Toast
