import { createPortal } from 'react-dom'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import useModalDialog from '../../hooks/useModalDialog'
import './ConfirmDialog.css'

// Asks before something irreversible happens. A native modal <dialog> portaled to
// <body> — same mechanics as the other modals in the app: the browser owns the
// backdrop, the focus trap and Escape, and the parent owns the open state by
// mounting or unmounting this.
//
// `destructive` paints the confirm button in the danger colour. While `busy` the
// dialog stays open with both buttons disabled and the confirm label swapped for
// `busyLabel`, so the user is not left wondering whether their click registered —
// and Escape and the backdrop stop closing it mid-flight.
const ConfirmDialog = ({
  title,
  message,
  confirmLabel,
  busyLabel,
  cancelLabel,
  destructive = false,
  busy = false,
  onConfirm,
  onCancel,
}) => {
  // A re-render (e.g. `busy` flipping) must not close and reopen the dialog —
  // see useModalDialog for how the ref callback pins it to the element.
  const attachDialog = useModalDialog(busy ? undefined : onCancel)

  return createPortal(
    <dialog ref={attachDialog} className="confirm-dialog" aria-labelledby="confirm-dialog-title">
      <div className="confirm-dialog-content">
        <header className="confirm-dialog-header">
          <span
            className={
              destructive
                ? 'confirm-dialog-icon confirm-dialog-icon--danger'
                : 'confirm-dialog-icon'
            }
          >
            <WarningAmberRoundedIcon sx={{ fontSize: 20 }} />
          </span>
          <h3 id="confirm-dialog-title" className="confirm-dialog-title">
            {title}
          </h3>
        </header>

        <p className="confirm-dialog-message">{message}</p>

        <div className="confirm-dialog-actions">
          <button
            type="button"
            className="confirm-dialog-button confirm-dialog-button--ghost"
            onClick={onCancel}
            disabled={busy}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            className={
              destructive
                ? 'confirm-dialog-button confirm-dialog-button--danger'
                : 'confirm-dialog-button'
            }
            onClick={onConfirm}
            disabled={busy}
          >
            {busy ? busyLabel : confirmLabel}
          </button>
        </div>
      </div>
    </dialog>,
    document.body,
  )
}

export default ConfirmDialog
