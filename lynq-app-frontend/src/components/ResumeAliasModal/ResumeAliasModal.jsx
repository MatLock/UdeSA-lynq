import { useState } from 'react'
import { createPortal } from 'react-dom'
import DriveFileRenameOutlineRoundedIcon from '@mui/icons-material/DriveFileRenameOutlineRounded'
import useModalDialog from '../../hooks/useModalDialog'
import strings from '../../i18n'
import './ResumeAliasModal.css'

// The backend caps the alias column at 100 characters; enforcing it on the
// input keeps the dialog from offering something the save would reject.
const MAX_ALIAS_LENGTH = 100

// Modal that assigns (or replaces — same operation) the alias of one resume.
// Same mechanics as ConfirmDialog: a native modal <dialog> portaled to <body>,
// where the browser owns the backdrop, the focus trap and Escape, and the
// parent owns the open state by mounting or unmounting this.
//
// One choice and nothing else: the alias itself, prefilled with the current
// one so renaming starts from what is there. Confirming keeps the dialog
// mounted with the controls disabled (`busy`) while the parent runs the save —
// the call is quick, so no full-screen overlay is needed.
const ResumeAliasModal = ({ resume, busy = false, onConfirm, onCancel }) => {
  const t = strings.pages.resume.alias

  const [alias, setAlias] = useState(resume?.alias ?? '')

  const trimmed = alias.trim()
  const ready = !busy && trimmed.length > 0

  const attachDialog = useModalDialog(onCancel)

  const handleSubmit = (event) => {
    event.preventDefault()
    if (!ready) return
    onConfirm(trimmed)
  }

  return createPortal(
    <dialog
      ref={attachDialog}
      className="resume-alias-dialog"
      aria-labelledby="resume-alias-title"
    >
      <form className="resume-alias-content" onSubmit={handleSubmit}>
        <header className="resume-alias-header">
          <span className="resume-alias-icon">
            <DriveFileRenameOutlineRoundedIcon sx={{ fontSize: 20 }} />
          </span>
          <h3 id="resume-alias-title" className="resume-alias-title">
            {t.title}
          </h3>
        </header>

        <p className="resume-alias-subtitle">{t.subtitle}</p>

        <label className="resume-alias-field">
          <span className="resume-alias-label">{t.label}</span>
          <input
            type="text"
            className="resume-alias-input"
            value={alias}
            maxLength={MAX_ALIAS_LENGTH}
            placeholder={t.placeholder}
            disabled={busy}
            autoFocus
            onChange={(event) => setAlias(event.target.value)}
          />
        </label>

        <div className="resume-alias-actions">
          <button
            type="button"
            className="resume-alias-button resume-alias-button--ghost"
            disabled={busy}
            onClick={onCancel}
          >
            {t.cancel}
          </button>
          <button type="submit" className="resume-alias-button" disabled={!ready}>
            {busy ? t.saving : t.confirm}
          </button>
        </div>
      </form>
    </dialog>,
    document.body,
  )
}

export default ResumeAliasModal
