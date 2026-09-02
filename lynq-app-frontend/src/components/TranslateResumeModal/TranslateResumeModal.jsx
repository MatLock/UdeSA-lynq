import { useState } from 'react'
import { createPortal } from 'react-dom'
import TranslateRoundedIcon from '@mui/icons-material/TranslateRounded'
import Spinner from '../Spinner/Spinner.jsx'
import useModalDialog from '../../hooks/useModalDialog'
import strings from '../../i18n'
import './TranslateResumeModal.css'

// Modal that starts translating one of the candidate's resumes into another
// language. Same mechanics as ConfirmDialog: a native modal <dialog> portaled
// to <body>, where the browser owns the backdrop, the focus trap and Escape,
// and the parent owns the open state by mounting or unmounting this.
//
// Two choices and nothing else: which stored resume is the source and which
// language the copy is written in. The template is deliberately NOT chosen
// here — the candidate picks it after the translation ran, over a live preview
// (TranslateTemplateModal). The language options come from the backend's
// supported_languages table (never hardcoded), minus every language the
// candidate already holds a resume in — the backend rejects duplicates, so the
// dialog simply doesn't offer them. With no language left it says so instead
// of showing an empty select.
//
// Confirming hands the flow to the parent and this dialog unmounts right away;
// the parent covers the wait with a full-screen LoadingOverlay (the same
// pattern as the skill enhancement), so no busy state lives here.
const TranslateResumeModal = ({
  resumes,
  languages,
  loading = false,
  initialSourceId,
  onConfirm,
  onCancel,
}) => {
  const t = strings.pages.resume.translate

  // Languages the translation may target: any supported one no resume is
  // already written in. This also rules out the source's own language.
  const taken = new Set(
    resumes.map((resume) => (resume.language ?? '').toUpperCase()),
  )
  const available = languages.filter(
    (language) => !taken.has((language.code ?? '').toUpperCase()),
  )

  const [sourceId, setSourceId] = useState(
    initialSourceId ?? resumes[0]?.id ?? '',
  )
  const [languageCode, setLanguageCode] = useState('')

  // The default target is resolved at render time rather than in state, so it
  // stays valid when the languages finish loading after the first paint.
  const selectedLanguage = languageCode || available[0]?.code || ''
  const ready = !loading && sourceId && selectedLanguage

  const attachDialog = useModalDialog(onCancel)

  const handleSubmit = (event) => {
    event.preventDefault()
    if (!ready) return
    onConfirm(sourceId, selectedLanguage)
  }

  const renderChoices = () => {
    if (loading) {
      return (
        <div className="translate-resume-state">
          <Spinner label={t.loadingLanguages} />
        </div>
      )
    }

    if (available.length === 0) {
      return <p className="translate-resume-empty">{t.noLanguages}</p>
    }

    return (
      <>
        <label className="translate-resume-field">
          <span className="translate-resume-label">{t.sourceLabel}</span>
          <select
            className="translate-resume-select"
            value={sourceId}
            onChange={(event) => setSourceId(event.target.value)}
          >
            {resumes.map((resume) => (
              <option key={resume.id} value={resume.id}>
                {`${resume.alias || resume.name || t.untitledSource} (${resume.language})`}
              </option>
            ))}
          </select>
        </label>

        <label className="translate-resume-field">
          <span className="translate-resume-label">{t.languageLabel}</span>
          <select
            className="translate-resume-select"
            value={selectedLanguage}
            onChange={(event) => setLanguageCode(event.target.value)}
          >
            {available.map((language) => (
              <option key={language.code} value={language.code}>
                {language.name}
              </option>
            ))}
          </select>
        </label>

      </>
    )
  }

  return createPortal(
    <dialog
      ref={attachDialog}
      className="translate-resume-dialog"
      aria-labelledby="translate-resume-title"
    >
      <form className="translate-resume-content" onSubmit={handleSubmit}>
        <header className="translate-resume-header">
          <span className="translate-resume-icon">
            <TranslateRoundedIcon sx={{ fontSize: 20 }} />
          </span>
          <h3 id="translate-resume-title" className="translate-resume-title">
            {t.title}
          </h3>
        </header>

        <p className="translate-resume-subtitle">{t.subtitle}</p>

        {renderChoices()}

        <div className="translate-resume-actions">
          <button
            type="button"
            className="translate-resume-button translate-resume-button--ghost"
            onClick={onCancel}
          >
            {t.cancel}
          </button>
          <button
            type="submit"
            className="translate-resume-button"
            disabled={!ready || available.length === 0}
          >
            {t.confirm}
          </button>
        </div>
      </form>
    </dialog>,
    document.body,
  )
}

export default TranslateResumeModal
