import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import useApi from '../../hooks/useApi'
import useModalDialog from '../../hooks/useModalDialog'
import resumeService from '../../services/resumeService'
import resumeLabel from '../../utils/resumeLabel'
import formatResumeDate from '../../utils/formatResumeDate'
import strings from '../../i18n'
import './ApplyResumeModal.css'

// Applying is a choice, not a click: a candidate keeps several resumes (one per
// language, one per kind of role) and the recruiter only ever sees the one they
// applied with. This dialog is where that choice is made — the list of the
// candidate's stored resumes, each shown by the name they know it by.
//
// Same mechanics as ResumeAliasModal: a native modal <dialog> portaled to
// <body>, so the browser owns the backdrop, the focus trap and Escape, and the
// parent owns the open state by mounting or unmounting this.
//
// The list is loaded here rather than by the page: it is only ever needed once
// the dialog opens, and the page has no other use for it.
const ApplyResumeModal = ({ busy = false, onConfirm, onCancel }) => {
  const t = strings.jobDetail.applyDialog
  const { authFetch } = useApi()

  const [resumes, setResumes] = useState(null) // null while loading
  const [failed, setFailed] = useState(false)
  const [selectedId, setSelectedId] = useState('')

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const stored = await resumeService.get_resumes(authFetch)
        if (cancelled) return
        setResumes(stored)
        // Preselect the first so confirming is one click for the common case of
        // a candidate with a single resume.
        setSelectedId(stored[0]?.id ?? '')
      } catch {
        if (!cancelled) {
          setResumes([])
          setFailed(true)
        }
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [authFetch])

  const loading = resumes === null
  const empty = !loading && resumes.length === 0
  const ready = !busy && !loading && selectedId !== ''

  const attachDialog = useModalDialog(onCancel)

  const handleSubmit = (event) => {
    event.preventDefault()
    if (!ready) return
    onConfirm(selectedId)
  }

  const renderBody = () => {
    if (loading) return <p className="apply-resume-note">{t.loading}</p>
    if (failed) return <p className="apply-resume-note is-error">{t.loadError}</p>
    if (empty) {
      return (
        <p className="apply-resume-note">
          {t.emptyBody}{' '}
          <Link className="apply-resume-link" to="/my-resume" onClick={onCancel}>
            {t.emptyAction}
          </Link>
        </p>
      )
    }

    return (
      <ul className="apply-resume-list">
        {resumes.map((resume) => (
          <li key={resume.id}>
            <label
              className={
                resume.id === selectedId
                  ? 'apply-resume-option is-selected'
                  : 'apply-resume-option'
              }
            >
              <input
                type="radio"
                name="apply-resume"
                value={resume.id}
                checked={resume.id === selectedId}
                disabled={busy}
                onChange={() => setSelectedId(resume.id)}
              />
              <span className="apply-resume-option-text">
                <span className="apply-resume-option-name">
                  {resumeLabel(resume, t.untitled)}
                </span>
                <span className="apply-resume-option-meta">
                  {[resume.language, formatResumeDate.formatResumeCreatedOn(resume.createdOn)]
                    .filter(Boolean)
                    .join(' · ')}
                </span>
              </span>
            </label>
          </li>
        ))}
      </ul>
    )
  }

  return createPortal(
    <dialog
      ref={attachDialog}
      className="apply-resume-dialog"
      aria-labelledby="apply-resume-title"
    >
      <form className="apply-resume-content" onSubmit={handleSubmit}>
        <header className="apply-resume-header">
          <span className="apply-resume-icon">
            <DescriptionOutlinedIcon sx={{ fontSize: 20 }} />
          </span>
          <h3 id="apply-resume-title" className="apply-resume-title">
            {t.title}
          </h3>
        </header>

        <p className="apply-resume-subtitle">{t.subtitle}</p>

        {renderBody()}

        <div className="apply-resume-actions">
          <button
            type="button"
            className="apply-resume-button apply-resume-button--ghost"
            disabled={busy}
            onClick={onCancel}
          >
            {t.cancel}
          </button>
          <button type="submit" className="apply-resume-button" disabled={!ready}>
            {busy ? t.applying : t.confirm}
          </button>
        </div>
      </form>
    </dialog>,
    document.body,
  )
}

export default ApplyResumeModal
