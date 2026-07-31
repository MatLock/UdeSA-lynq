import { useEffect, useRef, useState } from 'react'
import { Navigate } from 'react-router-dom'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DownloadRoundedIcon from '@mui/icons-material/DownloadRounded'
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'
import ResumeCreation from '../../components/ResumeCreation/ResumeCreation.jsx'
import ResumeDocument from '../../components/ResumeDocument/ResumeDocument.jsx'
import ResumeSectionNav from '../../components/ResumeSectionNav/ResumeSectionNav.jsx'
import Spinner from '../../components/Spinner/Spinner.jsx'
import LynqTitle from '../../components/LynqTitle/LynqTitle.jsx'
import Toast from '../../components/Toast/Toast.jsx'
import ResumeWizardProvider from '../../context/ResumeWizardContext.jsx'
import useApi from '../../hooks/useApi'
import useAuth from '../../hooks/useAuth'
import useActiveSection from '../../hooks/useActiveSection'
import resumeService from '../../services/resumeService'
import resumeSections from '../../utils/resumeSections'
import strings, { activeLocale } from '../../i18n'
import './MyResumePage.css'

// The resume's own dates are partial (see utils/formatResumeDate); createdOn is a
// full backend LocalDate, so it is formatted here in the active UI locale.
const formatCreatedOn = (isoDate) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(isoDate ?? '')
  if (!match) return isoDate ?? ''
  const [, year, month, day] = match
  return new Intl.DateTimeFormat(activeLocale, { dateStyle: 'medium' }).format(
    new Date(Number(year), Number(month) - 1, Number(day)),
  )
}

// The candidate's "My Resume" section. It opens by asking the backend for every
// resume the user has (GET /user/resume) and branches on the answer:
//
//   no resume at all  → straight into the creation workflow (nothing to show)
//   one or more       → the viewer, defaulting to the resume written in the
//                       language the UI is configured in
//
// A candidate can hold the same resume in several languages, so "which resume to
// show" is a language match first and a manual choice second (the switcher above
// the document). When no resume exists in the UI language we show another one and
// say so, rather than pretending the section is empty.
const MyResumePage = () => {
  const t = strings.pages.resume
  const { authFetch } = useApi()
  const { user } = useAuth()

  const [resumes, setResumes] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [toast, setToast] = useState(null)
  // Id of the resume the user picked in the switcher; null means "follow the UI
  // language" (the default resolved below).
  const [selectedId, setSelectedId] = useState(null)
  // The user asked for another resume while already having one — show the wizard
  // over the viewer. The counter re-keys the provider so each run starts clean.
  const [creating, setCreating] = useState(false)
  const [wizardRun, setWizardRun] = useState(0)
  // A document was uploaded but no parsed resume has appeared yet: extraction
  // happens server-side, so the list can still be empty right afterwards.
  const [uploadPending, setUploadPending] = useState(false)

  // Bumped to re-run the fetch (retry, post-creation reload, manual refresh).
  const [reloadToken, setReloadToken] = useState(0)

  const documentRef = useRef(null)

  // Fetch the candidate's resumes, and refetch whenever the reload token changes.
  // A cancel flag drops the result of a superseded request so a slow earlier fetch
  // can't overwrite a newer one.
  useEffect(() => {
    let cancelled = false
    const loadResumes = async () => {
      setLoading(true)
      setError('')
      try {
        const result = await resumeService.get_resumes(authFetch)
        if (!cancelled) setResumes(Array.isArray(result) ? result : [])
      } catch (err) {
        if (!cancelled) setError(err.reason ?? err.message ?? t.loadError)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    loadResumes()

    return () => {
      cancelled = true
    }
  }, [authFetch, reloadToken, t.loadError])

  const reload = () => setReloadToken((previous) => previous + 1)

  // The resume the viewer shows: the user's explicit pick, else the one written in
  // the UI language, else whatever the candidate has.
  const preferredLanguage = activeLocale.toUpperCase()
  const languageMatch = resumes.find(
    (resume) => (resume.language ?? '').toUpperCase() === preferredLanguage,
  )
  const selected =
    resumes.find((resume) => resume.id === selectedId) ?? languageMatch ?? resumes[0]

  const sections = resumeSections.sectionsOf(selected?.resume)
  const sectionKey = `${selected?.id ?? ''}:${sections.map((section) => section.id).join('|')}`
  const { activeId, scrollTo } = useActiveSection(documentRef, sectionKey)

  const languageName = (code) => t.languageNames[(code ?? '').toUpperCase()] ?? code

  const startCreating = () => {
    setWizardRun((previous) => previous + 1)
    setCreating(true)
  }

  // The wizard finished. The form path persisted a resume, so reload and show it;
  // the upload path only stored a document, so flag that the parsed resume may not
  // exist yet — the pending panel stands in until a reload turns one up.
  const handleCompleted = (method) => {
    setCreating(false)
    if (method === 'form') {
      setToast({ type: 'success', message: t.create.success })
    } else {
      setUploadPending(true)
    }
    reload()
  }

  // Candidate-only page: send company users back to the feed (same as the
  // applications section).
  if (user?.userType === 'COMPANY') {
    return <Navigate to="/home" replace />
  }

  const renderWizard = () => (
    <ResumeWizardProvider key={wizardRun}>
      <ResumeCreation
        onCompleted={handleCompleted}
        // Nothing to go back to when the candidate has no resume yet.
        onCancel={resumes.length > 0 ? () => setCreating(false) : undefined}
      />
    </ResumeWizardProvider>
  )

  const renderPending = () => (
    <div className="resume-page-panel">
      <span className="resume-page-panel-icon">
        <AutoAwesomeOutlinedIcon sx={{ fontSize: 26 }} />
      </span>
      <h2 className="resume-page-panel-title">{t.create.pending.title}</h2>
      <p className="resume-page-panel-body">{t.create.pending.body}</p>
      <button type="button" className="resume-page-action" onClick={reload}>
        {t.create.pending.refresh}
      </button>
    </div>
  )

  const renderViewer = () => (
    <>
      <header className="resume-page-hero">
        <div className="resume-page-hero-text">
          <LynqTitle
            as="h1"
            className="resume-page-title"
            text={t.title}
            placement="leading"
          />
          <p className="resume-page-subtitle">{t.subtitle}</p>
        </div>

        <div className="resume-page-actions">
          {selected?.pdfUrl && (
            <a
              className="resume-page-action resume-page-action--ghost"
              href={selected.pdfUrl}
              target="_blank"
              rel="noreferrer"
            >
              <DownloadRoundedIcon sx={{ fontSize: 17 }} />
              {t.downloadPdf}
            </a>
          )}
          <button type="button" className="resume-page-action" onClick={startCreating}>
            <AddRoundedIcon sx={{ fontSize: 17 }} />
            {t.newResume}
          </button>
        </div>
      </header>

      {/* Switcher only earns its space once there is more than one resume. */}
      {resumes.length > 1 && (
        <div className="resume-page-tabs" role="tablist" aria-label={t.pickResume}>
          {resumes.map((resume) => {
            const active = resume.id === selected?.id
            return (
              <button
                key={resume.id}
                type="button"
                role="tab"
                aria-selected={active}
                className={active ? 'resume-page-tab active' : 'resume-page-tab'}
                onClick={() => setSelectedId(resume.id)}
              >
                <PictureAsPdfOutlinedIcon sx={{ fontSize: 15 }} />
                <span className="resume-page-tab-name">{resume.name || t.untitled}</span>
                <span className="resume-page-tab-language">{resume.language}</span>
              </button>
            )
          })}
        </div>
      )}

      <div className="resume-page-meta">
        {selected?.createdOn && (
          <span className="resume-page-created">
            {t.createdOn.replace('{date}', formatCreatedOn(selected.createdOn))}
          </span>
        )}
        {selected &&
          (selected.language ?? '').toUpperCase() !== preferredLanguage && (
            <span className="resume-page-note">
              {t.languageFallback
                .replace('{expected}', languageName(preferredLanguage))
                .replace('{shown}', languageName(selected.language))}
            </span>
          )}
      </div>

      <div className="resume-page-body">
        <ResumeSectionNav
          sections={sections}
          activeId={activeId}
          onSelect={scrollTo}
        />
        <main className="resume-page-document" ref={documentRef}>
          {sections.length > 0 ? (
            <ResumeDocument resume={selected.resume} sections={sections} />
          ) : (
            <p className="resume-page-empty">{t.noContent}</p>
          )}
        </main>
      </div>
    </>
  )

  const renderContent = () => {
    if (loading) {
      return (
        <div className="resume-page-state">
          <Spinner label={t.loading} />
        </div>
      )
    }

    if (error) {
      return (
        <div className="resume-page-state">
          <p className="resume-page-error" role="alert">
            {error}
          </p>
          <button type="button" className="resume-page-action" onClick={reload}>
            {t.retry}
          </button>
        </div>
      )
    }

    // No resume on file (or the candidate asked for another one) — the creation
    // workflow is the whole page.
    if (creating || resumes.length === 0) {
      return uploadPending && resumes.length === 0 ? renderPending() : renderWizard()
    }

    return renderViewer()
  }

  // The viewer pins its header and scrolls only the document; the wizard is one
  // tall card, so that mode scrolls as a whole page instead.
  const scrolls = loading || error || creating || resumes.length === 0

  return (
    <div className={scrolls ? 'resume-page resume-page--scroll' : 'resume-page'}>
      <Toast
        message={toast?.message}
        type={toast?.type}
        onClose={() => setToast(null)}
      />
      {renderContent()}
    </div>
  )
}

export default MyResumePage
