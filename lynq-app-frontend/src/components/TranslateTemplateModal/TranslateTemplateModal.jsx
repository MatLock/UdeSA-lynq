import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Document, Page, pdfjs } from 'react-pdf'
import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined'
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import TranslateRoundedIcon from '@mui/icons-material/TranslateRounded'
import ViewSidebarOutlinedIcon from '@mui/icons-material/ViewSidebarOutlined'
import LoadingOverlay from '../LoadingOverlay/LoadingOverlay.jsx'
import ResumeOptionCards from '../ResumeOptionCards/ResumeOptionCards.jsx'
import Spinner from '../Spinner/Spinner.jsx'
import useApi from '../../hooks/useApi'
import useRotatingPhrase from '../../hooks/useRotatingPhrase'
import resumeService from '../../services/resumeService'
import strings from '../../i18n'
import workerSrc from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import 'react-pdf/dist/Page/TextLayer.css'
import 'react-pdf/dist/Page/AnnotationLayer.css'
import './TranslateTemplateModal.css'

pdfjs.GlobalWorkerOptions.workerSrc = workerSrc

const DEFAULT_TEMPLATE = 'MODERN'
const NOT_LOADED = { pages: 0, failed: false }

// Second half of the translation flow: the resume is already translated (the
// parent holds the JSON) and the candidate now decides how it looks. They pick
// a template, render a live preview of the translated document with it, and
// only their confirmation stores the resume — the same
// preview-file-then-confirm contract the creation wizard uses, so an abandoned
// preview never becomes a resume.
//
// Deliberately NOT a native <dialog>: pdf.js leaves the canvas blank inside
// one (react-pdf #225/#1005), so this is a plain fixed overlay portaled to
// <body> — the same rendering conditions as the wizard's preview step, where
// the PDF is known to draw. Escape and a click on the scrim cancel, keeping
// the dialog feel. Switching templates discards the previous preview file
// before rendering the next one, and cancelling discards whatever preview
// exists.
const TranslateTemplateModal = ({ source, language, resume, onCompleted, onCancel }) => {
  const t = strings.pages.resume.translate.template
  // The wizard's template step owns the card names and the rendering phrases;
  // reusing them keeps both flows telling the same story.
  const tc = strings.pages.resume.create.template
  const tp = strings.pages.resume.create.preview

  const { authFetch, freshAuthFetch } = useApi()

  const [template, setTemplate] = useState(DEFAULT_TEMPLATE)
  // The preview rendered for the CURRENT template; cleared whenever the
  // template changes, so the confirm button can never store a mismatched pair.
  const [preview, setPreview] = useState(null)
  const [loaded, setLoaded] = useState({ url: null, ...NOT_LOADED })
  const [rendering, setRendering] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [pageWidth, setPageWidth] = useState(0)

  const columnRef = useRef(null)
  const busy = rendering || saving
  const renderingPhrase = useRotatingPhrase(tc.rendering, rendering)
  const { pages, failed } = loaded.url === preview?.pdfUrl ? loaded : NOT_LOADED

  // Without a native <dialog> the browser no longer handles Escape; wire it up
  // so the overlay still behaves like one.
  const cancelRef = useRef(null)
  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape') cancelRef.current?.()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [])

  useLayoutEffect(() => {
    const column = columnRef.current
    if (!column) return

    const measure = () => setPageWidth(column.clientWidth)
    measure()

    const observer = new ResizeObserver(measure)
    observer.observe(column)
    return () => observer.disconnect()
  }, [])

  const options = [
    {
      value: 'CLASSIC',
      tone: 'purple',
      title: tc.classic,
      description: tc.classicDesc,
      Icon: ArticleOutlinedIcon,
    },
    {
      value: 'MODERN',
      tone: 'blue',
      title: tc.modern,
      description: tc.modernDesc,
      Icon: ViewSidebarOutlinedIcon,
    },
  ]

  // Best effort: the preview file is unreachable from the product once this
  // dialog lets go of it, so a failure to delete it only leaves an orphan.
  const discardPreview = (fileId) => {
    if (!fileId) return
    resumeService.delete_resume_preview(authFetch, fileId).catch(() => {})
  }

  const changeTemplate = (picked) => {
    if (busy || picked === template) return
    // The preview on screen belongs to the previous template; drop it.
    discardPreview(preview?.fileId)
    setPreview(null)
    setError('')
    setTemplate(picked)
  }

  // freshAuthFetch, not authFetch: rendering goes through lynq-ml and can take
  // a while, so the flow leaves with a token that has its whole lifetime ahead.
  const renderPreview = async () => {
    if (busy) return

    setRendering(true)
    setError('')
    try {
      const rendered = await resumeService.preview_resume(freshAuthFetch, {
        resume,
        template,
      })
      setPreview(rendered)
    } catch (renderError) {
      setError(renderError.reason ?? renderError.message ?? tc.renderError)
    } finally {
      setRendering(false)
    }
  }

  // The confirmation: the previewed PDF becomes the stored resume. The name
  // mirrors what the gateway used to do — keep the source's display name so the
  // candidate still recognizes it in the switcher, falling back to the
  // translated document's own full name.
  const confirm = async () => {
    if (busy || !preview) return

    setSaving(true)
    setError('')
    try {
      const created = await resumeService.create_resume(authFetch, {
        name:
          source?.name ||
          resume?.personal_info?.full_name ||
          strings.pages.resume.untitled,
        language,
        resume,
        fileId: preview.fileId,
      })
      onCompleted?.(created)
    } catch (saveError) {
      setError(saveError.reason ?? saveError.message ?? t.saveError)
      setSaving(false)
    }
  }

  const cancel = () => {
    if (busy) return
    discardPreview(preview?.fileId)
    onCancel()
  }
  cancelRef.current = cancel

  return createPortal(
    <div
      className="translate-template-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="translate-template-title"
      onClick={(event) => {
        // A click on the scrim cancels; clicks on the card land on children.
        if (event.target === event.currentTarget) cancel()
      }}
    >
      {rendering && <LoadingOverlay label={renderingPhrase} />}
      {saving && <LoadingOverlay label={t.saving} />}

      <div className="translate-template-content">
        <header className="translate-template-header">
          <span className="translate-template-icon">
            <TranslateRoundedIcon sx={{ fontSize: 20 }} />
          </span>
          <h3 id="translate-template-title" className="translate-template-title">
            {t.title}
          </h3>
        </header>

        <p className="translate-template-subtitle">
          {t.subtitle.replace('{language}', language)}
        </p>

        <ResumeOptionCards
          name="translateTemplate"
          value={template}
          options={options}
          onChange={changeTemplate}
        />

        <div className="translate-template-frame">
          <div className="translate-template-column" ref={columnRef}>
            {preview?.pdfUrl ? (
              <Document
                file={preview.pdfUrl}
                onLoadSuccess={({ numPages }) =>
                  setLoaded({ url: preview.pdfUrl, pages: numPages, failed: false })
                }
                onLoadError={() =>
                  setLoaded({ url: preview.pdfUrl, pages: 0, failed: true })
                }
                loading={<Spinner label={tp.loading} />}
                error={<p className="translate-template-error">{tp.loadError}</p>}
                noData={<p className="translate-template-error">{tp.loadError}</p>}
              >
                {Array.from({ length: pages }, (_, index) => (
                  <Page
                    key={index}
                    pageNumber={index + 1}
                    width={pageWidth || undefined}
                    className="translate-template-page"
                  />
                ))}
              </Document>
            ) : (
              <p className="translate-template-empty">{t.empty}</p>
            )}
          </div>
        </div>

        {preview?.pdfUrl && (
          <div className="translate-template-meta">
            {pages > 0 && (
              <span className="translate-template-pages">
                {tp.pageCount.replace('{count}', pages)}
              </span>
            )}
            <a
              className="translate-template-open"
              href={preview.pdfUrl}
              target="_blank"
              rel="noreferrer"
            >
              <OpenInNewRoundedIcon sx={{ fontSize: 15 }} />
              {tp.openInTab}
            </a>
          </div>
        )}

        {error && (
          <p className="translate-template-error" role="alert">
            {error}
          </p>
        )}

        <div className="translate-template-actions">
          <button
            type="button"
            className="translate-template-button translate-template-button--ghost"
            disabled={busy}
            onClick={cancel}
          >
            {strings.pages.resume.translate.cancel}
          </button>
          {preview ? (
            <button
              type="button"
              className="translate-template-button"
              disabled={busy || failed}
              onClick={confirm}
            >
              {t.confirm}
            </button>
          ) : (
            <button
              type="button"
              className="translate-template-button"
              disabled={busy}
              onClick={renderPreview}
            >
              {t.generate}
            </button>
          )}
        </div>
      </div>
    </div>,
    document.body,
  )
}

export default TranslateTemplateModal
