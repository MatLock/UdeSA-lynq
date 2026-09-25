import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import AutoFixHighOutlinedIcon from '@mui/icons-material/AutoFixHighOutlined'
import CloseRoundedIcon from '@mui/icons-material/CloseRounded'
import ConfirmDialog from '../ConfirmDialog/ConfirmDialog.jsx'
import ResumeDocument from '../ResumeDocument/ResumeDocument.jsx'
import Spinner from '../Spinner/Spinner.jsx'
import useApi from '../../hooks/useApi'
import useModalDialog from '../../hooks/useModalDialog'
import useRotatingPhrase from '../../hooks/useRotatingPhrase'
import useTailorConversation from '../../hooks/useTailorConversation'
import resumeService from '../../services/resumeService'
import downloadFile from '../../utils/downloadFile'
import formatResumeDate from '../../utils/formatResumeDate'
import resumeLabel from '../../utils/resumeLabel'
import resumeSections from '../../utils/resumeSections'
import tailorChangedSections from '../../utils/tailorChangedSections'
import strings from '../../i18n'
import './TailorResumeModal.css'

const PICK_RESUME = 'PICK_RESUME'
const CHAT = 'CHAT'
const SAVED = 'SAVED'
const DONE = 'DONE'

const EXHAUSTED = 'EXHAUSTED'
const DEFAULT_TEMPLATE = 'MODERN'
const MAX_ALIAS_LENGTH = 100

const aliasFor = (job) =>
  [job?.title, job?.company?.name]
    .filter(Boolean)
    .join(' — ')
    .slice(0, MAX_ALIAS_LENGTH)

const TailorResumeModal = ({
  job,
  jobId,
  isExternal = false,
  externalUrl = '',
  sourceLabel = '',
  onApplied,
  onClose,
}) => {
  const t = strings.jobDetail.tailorDialog
  const td = strings.jobDetail
  const { authFetch, freshAuthFetch } = useApi()
  const conversation = useTailorConversation(jobId)

  const [resumes, setResumes] = useState(null)
  const [loadFailed, setLoadFailed] = useState(false)
  const [selectedId, setSelectedId] = useState('')
  const [workingResume, setWorkingResume] = useState(null)
  const [stage, setStage] = useState(PICK_RESUME)
  const [draft, setDraft] = useState('')
  const [mobileTab, setMobileTab] = useState('chat')
  const [saving, setSaving] = useState(false)
  const [saveFailed, setSaveFailed] = useState(false)
  const [savedResume, setSavedResume] = useState(null)
  const [outcome, setOutcome] = useState('')
  const [externalBusy, setExternalBusy] = useState(false)
  const [confirmingClose, setConfirmingClose] = useState(false)

  const previewFileIdRef = useRef('')
  const beginWithRef = useRef(null)
  const transcriptRef = useRef(null)

  const startingPhrase = useRotatingPhrase(t.starting, conversation.starting)
  const thinkingPhrase = useRotatingPhrase(t.thinking, conversation.thinking)
  const savingPhrase = useRotatingPhrase(t.applying, saving)

  const beginWith = async (storedResume) => {
    if (!storedResume) return
    setWorkingResume(storedResume)
    const started = await conversation.start(storedResume)
    if (started) setStage(CHAT)
  }
  useEffect(() => {
    beginWithRef.current = beginWith
  })

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const stored = await resumeService.get_resumes(authFetch)
        if (cancelled) return
        setResumes(stored)
        setSelectedId(stored[0]?.id ?? '')
        if (stored.length === 1) await beginWithRef.current(stored[0])
      } catch {
        if (cancelled) return
        setResumes([])
        setLoadFailed(true)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [authFetch])

  useEffect(() => {
    const transcript = transcriptRef.current
    if (transcript) transcript.scrollTop = transcript.scrollHeight
  }, [conversation.messages, conversation.thinking])

  const busy = conversation.starting || conversation.thinking || saving || externalBusy
  const isExhausted = conversation.status === EXHAUSTED
  const shownResume = conversation.resume ?? workingResume?.resume ?? null
  const changedSections = tailorChangedSections(conversation.changes)
  const canApply =
    Boolean(shownResume) && (conversation.version > 0 || isExhausted) && !busy

  const turnsLeftLabel = () => {
    if (conversation.turnsLeft == null) return ''
    if (conversation.turnsLeft === 0) return t.turnsLeftNone
    if (conversation.turnsLeft === 1) return t.turnsLeftOne
    return t.turnsLeft.replace('{count}', conversation.turnsLeft)
  }

  const discardPreview = () => {
    const fileId = previewFileIdRef.current
    if (!fileId) return
    previewFileIdRef.current = ''
    resumeService.delete_resume_preview(authFetch, fileId).catch(() => {})
  }

  const closeNow = () => {
    discardPreview()
    onClose()
  }

  const requestClose = () => {
    if (busy) return
    if (stage === CHAT && conversation.version > 0) {
      setConfirmingClose(true)
      return
    }
    closeNow()
  }

  const restart = () => {
    if (busy) return
    conversation.reset()
    setDraft('')
    if (resumes?.length === 1) {
      beginWith(resumes[0])
      return
    }
    setWorkingResume(null)
    setStage(PICK_RESUME)
  }

  const submitMessage = (event) => {
    event.preventDefault()
    if (!draft.trim() || conversation.thinking || isExhausted) return
    const message = draft
    setDraft('')
    conversation.send(message)
  }

  const storeTailoredResume = async () => {
    const preview = await resumeService.preview_resume(freshAuthFetch, {
      resume: shownResume,
      template: DEFAULT_TEMPLATE,
    })
    previewFileIdRef.current = preview.fileId

    const created = await resumeService.create_resume(authFetch, {
      name:
        workingResume?.name ||
        shownResume?.personal_info?.full_name ||
        strings.pages.resume.untitled,
      language: workingResume?.language,
      resume: shownResume,
      fileId: preview.fileId,
      tailoredForJobId: jobId,
    })
    previewFileIdRef.current = ''

    const alias = aliasFor(job)
    if (!alias) return created
    try {
      const aliased = await resumeService.assign_alias(authFetch, created.id, alias)
      return { ...created, ...aliased }
    } catch {
      return created
    }
  }

  const applyWithTailoredResume = async () => {
    if (!canApply) return
    setSaving(true)
    setSaveFailed(false)
    try {
      const stored = await storeTailoredResume()
      setSavedResume(stored)

      if (isExternal) {
        setStage(SAVED)
        return
      }

      const applied = await resumeService.tailor_apply(
        authFetch,
        conversation.conversationId,
        stored.id,
      )
      const result = applied?.alreadyApplied ? 'already' : 'applied'
      setOutcome(result)
      setStage(DONE)
      onApplied?.(result)
    } catch {
      setSaveFailed(true)
    } finally {
      setSaving(false)
    }
  }

  const registerExternalApplication = async () => {
    try {
      await downloadFile(
        savedResume?.pdfUrl,
        `${resumeLabel(savedResume, t.untitled)}.pdf`,
      )
    } catch {
      setOutcome('downloadError')
      setExternalBusy(false)
      onApplied?.('downloadError')
      return
    }

    try {
      await resumeService.tailor_apply(
        authFetch,
        conversation.conversationId,
        savedResume.id,
      )
      setOutcome('redirected')
      onApplied?.('redirected')
    } catch {
      setOutcome('externalRegisterError')
      onApplied?.('externalRegisterError')
    }
    setExternalBusy(false)
  }

  const finishExternally = () => {
    if (externalBusy) return
    window.open(externalUrl, '_blank', 'noopener,noreferrer')
    setExternalBusy(true)
    setOutcome('')
    registerExternalApplication()
  }

  const attachDialog = useModalDialog(confirmingClose ? undefined : requestClose)

  const renderPicker = () => {
    if (resumes === null || conversation.starting) {
      return (
        <div className="tailor-resume-state">
          <Spinner label={conversation.starting ? startingPhrase : t.loading} />
        </div>
      )
    }
    if (loadFailed) return <p className="tailor-resume-note is-error">{t.loadError}</p>
    if (resumes.length === 0) {
      return (
        <p className="tailor-resume-note">
          {td.applyDialog.emptyBody}{' '}
          <Link className="tailor-resume-link" to="/my-resume" onClick={closeNow}>
            {td.applyDialog.emptyAction}
          </Link>
        </p>
      )
    }

    return (
      <form
        className="tailor-resume-picker"
        onSubmit={(event) => {
          event.preventDefault()
          beginWith(resumes.find((stored) => stored.id === selectedId))
        }}
      >
        <h4 className="tailor-resume-pick-title">{t.pickTitle}</h4>
        <p className="tailor-resume-pick-subtitle">{t.pickSubtitle}</p>
        <ul className="tailor-resume-list">
          {resumes.map((stored) => (
            <li key={stored.id}>
              <label
                className={
                  stored.id === selectedId
                    ? 'tailor-resume-option is-selected'
                    : 'tailor-resume-option'
                }
              >
                <input
                  type="radio"
                  name="tailor-resume"
                  value={stored.id}
                  checked={stored.id === selectedId}
                  onChange={() => setSelectedId(stored.id)}
                />
                <span className="tailor-resume-option-text">
                  <span className="tailor-resume-option-name">
                    {resumeLabel(stored, t.untitled)}
                  </span>
                  <span className="tailor-resume-option-meta">
                    {[
                      stored.language,
                      formatResumeDate.formatResumeCreatedOn(stored.createdOn),
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                </span>
              </label>
            </li>
          ))}
        </ul>
        {conversation.failure === 'start' && (
          <p className="tailor-resume-note is-error">{t.startError}</p>
        )}
        <div className="tailor-resume-actions">
          <button
            type="button"
            className="tailor-resume-button tailor-resume-button--ghost"
            onClick={closeNow}
          >
            {t.close}
          </button>
          <button
            type="submit"
            className="tailor-resume-button"
            disabled={!selectedId}
          >
            {t.start}
          </button>
        </div>
      </form>
    )
  }

  const renderTranscript = () => (
    <div className="tailor-resume-chat">
      <p className="tailor-resume-based-on">
        {t.basedOn.replace('{resume}', resumeLabel(workingResume, t.untitled))}
      </p>

      <div className="tailor-resume-transcript" ref={transcriptRef}>
        {conversation.messages.map((message, index) => (
          <div
            key={`${message.role}-${index}`}
            className={`tailor-resume-message is-${message.role}`}
          >
            <p className="tailor-resume-bubble">{message.content}</p>
            {message.warnings?.length > 0 && (
              <div className="tailor-resume-warnings">
                <span className="tailor-resume-warnings-title">{t.warningsTitle}</span>
                <ul>
                  {message.warnings.map((warning) => (
                    <li key={warning}>{warning}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        ))}
        {conversation.thinking && (
          <div className="tailor-resume-message is-assistant">
            <p className="tailor-resume-bubble is-thinking">{thinkingPhrase}</p>
          </div>
        )}
      </div>

      {conversation.failure === 'turn' && (
        <p className="tailor-resume-note is-error">{t.turnError}</p>
      )}

      {isExhausted ? (
        <p className="tailor-resume-note is-info">{t.exhausted}</p>
      ) : (
        <form className="tailor-resume-composer" onSubmit={submitMessage}>
          <input
            type="text"
            className="tailor-resume-input"
            value={draft}
            placeholder={t.messagePlaceholder}
            disabled={conversation.thinking}
            onChange={(event) => setDraft(event.target.value)}
          />
          <button
            type="submit"
            className="tailor-resume-button"
            disabled={conversation.thinking || !draft.trim()}
          >
            {t.send}
          </button>
        </form>
      )}

      {turnsLeftLabel() && (
        <p className="tailor-resume-turns">{turnsLeftLabel()}</p>
      )}
    </div>
  )

  const renderDocument = () => (
    <div className="tailor-resume-preview">
      <header className="tailor-resume-preview-header">
        <h4 className="tailor-resume-preview-title">{t.documentHeading}</h4>
        <span className="tailor-resume-version">
          {conversation.version > 0
            ? t.versionLabel.replace('{version}', conversation.version)
            : t.baseVersion}
        </span>
      </header>

      {conversation.changes.length > 0 && (
        <div className="tailor-resume-changes">
          <span className="tailor-resume-changes-title">{t.changesTitle}</span>
          <ul>
            {conversation.changes.map((change, index) => (
              <li key={`${change.section}-${index}`}>{change.detail}</li>
            ))}
          </ul>
          <p className="tailor-resume-changed-hint">{t.changedHint}</p>
        </div>
      )}

      <div
        className="tailor-resume-document"
        data-changed={changedSections.join(' ')}
      >
        {shownResume && (
          <ResumeDocument
            resume={shownResume}
            sections={resumeSections.sectionsOf(shownResume)}
          />
        )}
      </div>
    </div>
  )

  const renderChat = () => (
    <>
      <div className="tailor-resume-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={mobileTab === 'chat'}
          className={
            mobileTab === 'chat' ? 'tailor-resume-tab is-active' : 'tailor-resume-tab'
          }
          onClick={() => setMobileTab('chat')}
        >
          {t.chatTab}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mobileTab === 'document'}
          className={
            mobileTab === 'document'
              ? 'tailor-resume-tab is-active'
              : 'tailor-resume-tab'
          }
          onClick={() => setMobileTab('document')}
        >
          {t.documentTab}
        </button>
      </div>

      <div className="tailor-resume-columns" data-tab={mobileTab}>
        <div className="tailor-resume-col tailor-resume-col--chat">
          {renderTranscript()}
        </div>
        <div className="tailor-resume-col tailor-resume-col--document">
          {renderDocument()}
        </div>
      </div>

      {saveFailed && <p className="tailor-resume-note is-error">{t.applyError}</p>}

      <div className="tailor-resume-actions">
        <button
          type="button"
          className="tailor-resume-button tailor-resume-button--ghost"
          disabled={busy}
          onClick={restart}
        >
          {t.restart}
        </button>
        <button
          type="button"
          className="tailor-resume-button"
          disabled={!canApply}
          onClick={applyWithTailoredResume}
        >
          {saving ? savingPhrase : t.apply}
        </button>
      </div>
    </>
  )

  const renderSaved = () => (
    <div className="tailor-resume-result">
      <h4 className="tailor-resume-result-title">{t.externalTitle}</h4>
      <p className="tailor-resume-note is-info">
        {t.externalBody.replace('{source}', sourceLabel)}
      </p>

      {outcome === 'redirected' && (
        <p className="tailor-resume-note is-success">
          {td.externalRedirected.replace('{source}', sourceLabel)}
        </p>
      )}
      {outcome === 'downloadError' && (
        <p className="tailor-resume-note is-error">
          {td.externalDownloadError.replace('{source}', sourceLabel)}
        </p>
      )}
      {outcome === 'externalRegisterError' && (
        <p className="tailor-resume-note is-error">
          {td.externalRegisterError.replace('{source}', sourceLabel)}
        </p>
      )}

      <div className="tailor-resume-actions">
        <button
          type="button"
          className="tailor-resume-button tailor-resume-button--ghost"
          disabled={externalBusy}
          onClick={closeNow}
        >
          {outcome === 'redirected' ? t.close : t.externalDismiss}
        </button>
        {outcome !== 'redirected' && (
          <button
            type="button"
            className="tailor-resume-button"
            disabled={externalBusy}
            onClick={finishExternally}
          >
            {outcome ? t.externalRetry : t.externalConfirm}
          </button>
        )}
      </div>
    </div>
  )

  const renderDone = () => (
    <div className="tailor-resume-result">
      <p className="tailor-resume-note is-success">
        {outcome === 'already' ? t.appliedAlready : t.applied}
      </p>
      <div className="tailor-resume-actions">
        <button type="button" className="tailor-resume-button" onClick={closeNow}>
          {t.close}
        </button>
      </div>
    </div>
  )

  const renderStage = () => {
    if (stage === PICK_RESUME) return renderPicker()
    if (stage === CHAT) return renderChat()
    if (stage === SAVED) return renderSaved()
    return renderDone()
  }

  return createPortal(
    <>
      <dialog
        ref={attachDialog}
        className="tailor-resume-dialog"
        aria-labelledby="tailor-resume-title"
      >
        <div className="tailor-resume-content">
          <header className="tailor-resume-header">
            <span className="tailor-resume-icon">
              <AutoFixHighOutlinedIcon sx={{ fontSize: 20 }} />
            </span>
            <div className="tailor-resume-heading">
              <h3 id="tailor-resume-title" className="tailor-resume-title">
                {t.title}
              </h3>
              <p className="tailor-resume-subtitle">{t.subtitle}</p>
            </div>
            <button
              type="button"
              className="tailor-resume-close"
              aria-label={t.close}
              disabled={busy}
              onClick={requestClose}
            >
              <CloseRoundedIcon sx={{ fontSize: 20 }} />
            </button>
          </header>

          {renderStage()}
        </div>
      </dialog>

      {confirmingClose && (
        <ConfirmDialog
          title={t.discardTitle}
          message={t.discardMessage}
          confirmLabel={t.discardConfirm}
          busyLabel={t.discardConfirm}
          cancelLabel={t.discardCancel}
          destructive
          onConfirm={closeNow}
          onCancel={() => setConfirmingClose(false)}
        />
      )}
    </>,
    document.body,
  )
}

export default TailorResumeModal
