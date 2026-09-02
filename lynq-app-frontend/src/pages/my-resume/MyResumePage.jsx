import { useEffect, useRef, useState } from 'react'
import { Navigate } from 'react-router-dom'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import DownloadRoundedIcon from '@mui/icons-material/DownloadRounded'
import DriveFileRenameOutlineRoundedIcon from '@mui/icons-material/DriveFileRenameOutlineRounded'
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined'
import TranslateRoundedIcon from '@mui/icons-material/TranslateRounded'
import ConfirmDialog from '../../components/ConfirmDialog/ConfirmDialog.jsx'
import LoadingOverlay from '../../components/LoadingOverlay/LoadingOverlay.jsx'
import ResumeAliasModal from '../../components/ResumeAliasModal/ResumeAliasModal.jsx'
import ResumeCreation from '../../components/ResumeCreation/ResumeCreation.jsx'
import ResumeDocument from '../../components/ResumeDocument/ResumeDocument.jsx'
import ResumeSectionNav from '../../components/ResumeSectionNav/ResumeSectionNav.jsx'
import Spinner from '../../components/Spinner/Spinner.jsx'
import LynqTitle from '../../components/LynqTitle/LynqTitle.jsx'
import Toast from '../../components/Toast/Toast.jsx'
import TranslateResumeModal from '../../components/TranslateResumeModal/TranslateResumeModal.jsx'
import TranslateTemplateModal from '../../components/TranslateTemplateModal/TranslateTemplateModal.jsx'
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
// the document). When no resume exists in the UI language we simply show another
// one, rather than pretending the section is empty.
const MyResumePage = () => {
  const t = strings.pages.resume
  const { authFetch, freshAuthFetch } = useApi()
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
  // The resume the user asked to delete, held until they confirm; null closes the
  // dialog. `deleting` keeps it open with the buttons disabled while it runs.
  const [pendingDeletion, setPendingDeletion] = useState(null)
  const [deleting, setDeleting] = useState(false)
  // The resume whose alias is being assigned or replaced; null closes the
  // dialog. `aliasBusy` keeps it open with the controls disabled while it runs.
  const [aliasTarget, setAliasTarget] = useState(null)
  const [aliasBusy, setAliasBusy] = useState(false)
  // The translation dialog: whether it is open, whether the flow is mid-flight,
  // and the supported languages it offers — fetched lazily the first time the
  // dialog opens (they come from the backend's supported_languages table, not
  // from code) and kept for later openings.
  const [translating, setTranslating] = useState(false)
  const [translateBusy, setTranslateBusy] = useState(false)
  const [languages, setLanguages] = useState([])
  const [languagesLoading, setLanguagesLoading] = useState(false)
  // The finished translation, waiting for the candidate to pick a template,
  // preview it and confirm: { source, language, resume }. Null when no
  // translation is mid-flow.
  const [translationDraft, setTranslationDraft] = useState(null)

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

  // What a resume is called everywhere on this page: the alias the candidate
  // assigned wins, falling back to the document's own name.
  const displayNameOf = (resume) => resume?.alias || resume?.name || t.untitled

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

  const startCreating = () => {
    setWizardRun((previous) => previous + 1)
    setCreating(true)
  }

  const startTranslating = async () => {
    setTranslating(true)
    if (languages.length > 0) return

    setLanguagesLoading(true)
    try {
      const result = await resumeService.get_supported_languages(authFetch)
      setLanguages(Array.isArray(result) ? result : [])
    } catch (error) {
      // Without the language list the dialog has nothing to offer — close it
      // and surface the failure the same way every other action does.
      setTranslating(false)
      setToast({
        type: 'error',
        message: error.reason ?? error.message ?? t.translate.error,
      })
    } finally {
      setLanguagesLoading(false)
    }
  }

  // First half of the flow: run the translation itself. The picker closes
  // right away and a full-screen LoadingOverlay takes over while it runs —
  // same pattern as the skill enhancement in the wizard (SkillsField). Nothing
  // is stored yet: the translated JSON opens the template/preview dialog,
  // where the candidate confirms (or walks away and nothing happened).
  //
  // freshAuthFetch, not authFetch: the gateway forwards this token across
  // services for as long as the model takes, so the flow must leave with a
  // token that has its whole lifetime ahead — one mid-life would expire
  // downstream, after the expensive translation already ran.
  const handleTranslate = async (sourceId, language) => {
    if (translateBusy) return

    setTranslating(false)
    setTranslateBusy(true)
    try {
      const translated = await resumeService.translate_resume(
        freshAuthFetch,
        sourceId,
        language,
      )
      setTranslationDraft({
        source: resumes.find((resume) => resume.id === sourceId) ?? null,
        language,
        resume: translated,
      })
    } catch (error) {
      setToast({
        type: 'error',
        message: error.reason ?? error.message ?? t.translate.error,
      })
    } finally {
      setTranslateBusy(false)
    }
  }

  // Second half: the candidate confirmed the previewed template, the resume is
  // stored. Reload and pin the selection to the new id so the viewer lands on
  // the translation when the list comes back.
  const handleTranslationStored = (created) => {
    setTranslationDraft(null)
    if (created?.id) setSelectedId(created.id)
    setToast({ type: 'success', message: t.translate.success })
    reload()
  }

  // Drop the resume, then reload: the viewer picks the next one on its own (the
  // UI-language match, else whatever is left), and falls through to the creation
  // wizard when that was the last one.
  const handleDelete = async () => {
    if (!pendingDeletion || deleting) return

    setDeleting(true)
    try {
      await resumeService.delete_resume(authFetch, pendingDeletion.id)
      // The selection is pinned by id; clear it so it can't point at a resume
      // that no longer exists.
      setSelectedId(null)
      setPendingDeletion(null)
      setToast({ type: 'success', message: t.deleteSuccess })
      reload()
    } catch (error) {
      setToast({
        type: 'error',
        message: error.reason ?? error.message ?? t.deleteError,
      })
    } finally {
      setDeleting(false)
    }
  }

  // Save the alias (assigning and replacing are the same call), then reload so
  // every list shows the new label. The selection is pinned to the same resume
  // so the viewer doesn't jump.
  const handleAliasSave = async (alias) => {
    if (!aliasTarget || aliasBusy) return

    setAliasBusy(true)
    try {
      await resumeService.assign_alias(authFetch, aliasTarget.id, alias)
      setSelectedId(aliasTarget.id)
      setAliasTarget(null)
      setToast({ type: 'success', message: t.alias.success })
      reload()
    } catch (error) {
      setToast({
        type: 'error',
        message: error.reason ?? error.message ?? t.alias.error,
      })
    } finally {
      setAliasBusy(false)
    }
  }

  const handleCompleted = () => {
    setCreating(false)
    setToast({ type: 'success', message: t.create.success })
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

        {/* The hero carries the one action that changes the collection; the
            actions that operate on a single resume live on its own toolbar,
            down at the document. */}
        <div className="resume-page-actions">
          <button
            type="button"
            className="resume-page-action resume-page-action--ghost"
            onClick={startTranslating}
          >
            <TranslateRoundedIcon sx={{ fontSize: 17 }} />
            {t.translateResume}
          </button>
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
                <span className="resume-page-tab-name">{displayNameOf(resume)}</span>
                <span className="resume-page-tab-language">{resume.language}</span>
              </button>
            )
          })}
        </div>
      )}

      <div className="resume-page-body">
        <ResumeSectionNav
          sections={sections}
          activeId={activeId}
          onSelect={scrollTo}
        />

        <div className="resume-page-doc-column">
          {/* Pinned to the top edge of the paper: what this document is on the
              left, what you can do to it on the right. */}
          <div className="resume-page-doc-bar">
            {selected?.createdOn && (
              <span className="resume-page-doc-date">
                {t.createdOn.replace('{date}', formatCreatedOn(selected.createdOn))}
              </span>
            )}
            <div className="resume-page-doc-actions">
              {selected?.pdfUrl && (
                <a
                  className="resume-page-doc-download"
                  href={selected.pdfUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  <DownloadRoundedIcon sx={{ fontSize: 16 }} />
                  {t.downloadPdf}
                </a>
              )}
              {/* The two pills that manage the resume itself sit together,
                  after the download. Assigning or replacing the alias is a
                  labeled pill, not a bare glyph: the feature is invisible
                  until the first alias exists, so the button has to say what
                  it does on its own. */}
              {selected && (
                <button
                  type="button"
                  className="resume-page-doc-rename"
                  onClick={() => setAliasTarget(selected)}
                >
                  <DriveFileRenameOutlineRoundedIcon sx={{ fontSize: 16 }} />
                  {selected.alias ? t.alias.editAction : t.alias.action}
                </button>
              )}
              {/* Rare and irreversible: a labeled pill like its neighbors, but
                  quiet until hover turns it red — one click from the
                  confirmation. */}
              {selected && (
                <button
                  type="button"
                  className="resume-page-doc-delete"
                  onClick={() => setPendingDeletion(selected)}
                >
                  <DeleteOutlineRoundedIcon sx={{ fontSize: 16 }} />
                  {t.deleteResume}
                </button>
              )}
            </div>
          </div>

          <main className="resume-page-document" ref={documentRef}>
            {sections.length > 0 ? (
              <ResumeDocument resume={selected.resume} sections={sections} />
            ) : (
              <p className="resume-page-empty">{t.noContent}</p>
            )}
          </main>
        </div>
      </div>
    </>
  )

  const renderContent = () => {
    // The wizard holds an unsaved draft, so it outranks the page's own loading
    // and error states: a background refetch must not unmount it and take the
    // half-written resume with it.
    if (creating) {
      return renderWizard()
    }

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

    // No resume on file — the creation workflow is the whole page.
    if (resumes.length === 0) {
      return renderWizard()
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

      {translating && (
        <TranslateResumeModal
          resumes={resumes}
          languages={languages}
          loading={languagesLoading}
          initialSourceId={selected?.id}
          onConfirm={handleTranslate}
          onCancel={() => setTranslating(false)}
        />
      )}

      {translateBusy && <LoadingOverlay label={t.translate.busy} />}

      {translationDraft && (
        <TranslateTemplateModal
          source={translationDraft.source}
          language={translationDraft.language}
          resume={translationDraft.resume}
          onCompleted={handleTranslationStored}
          onCancel={() => setTranslationDraft(null)}
        />
      )}

      {aliasTarget && (
        <ResumeAliasModal
          resume={aliasTarget}
          busy={aliasBusy}
          onConfirm={handleAliasSave}
          onCancel={() => setAliasTarget(null)}
        />
      )}

      {pendingDeletion && (
        <ConfirmDialog
          destructive
          busy={deleting}
          title={t.deleteTitle}
          message={t.deleteMessage.replace('{name}', displayNameOf(pendingDeletion))}
          confirmLabel={t.deleteConfirm}
          busyLabel={t.deleting}
          cancelLabel={t.create.cancel}
          onConfirm={handleDelete}
          onCancel={() => setPendingDeletion(null)}
        />
      )}
    </div>
  )
}

export default MyResumePage
