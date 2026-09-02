import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Document, Page, pdfjs } from 'react-pdf'
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import LoadingOverlay from '../LoadingOverlay/LoadingOverlay'
import Spinner from '../Spinner/Spinner'
import StepIndicator from '../StepIndicator/StepIndicator'
import Toast from '../Toast/Toast'
import useApi from '../../hooks/useApi'
import useResumeWizard from '../../hooks/useResumeWizard'
import resumeService from '../../services/resumeService'
import strings, { activeLocale } from '../../i18n'
import workerSrc from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import 'react-pdf/dist/Page/TextLayer.css'
import 'react-pdf/dist/Page/AnnotationLayer.css'
import './ResumePreviewStep.css'

pdfjs.GlobalWorkerOptions.workerSrc = workerSrc

const NOT_LOADED = { pages: 0, failed: false }

const resumeLanguage = () => {
  const code = activeLocale.toUpperCase()
  return resumeService.LANGUAGES.includes(code) ? code : 'EN'
}

const ResumePreviewStep = ({ active, stepNumber, totalSteps, onCompleted }) => {
  const t = strings.pages.resume.create
  const tp = t.preview
  const { authFetch } = useApi()
  const { data, updateData, back, setFooter } = useResumeWizard()

  const [loaded, setLoaded] = useState({ url: null, ...NOT_LOADED })
  const [saving, setSaving] = useState(false)
  const [discarding, setDiscarding] = useState(false)
  const [toast, setToast] = useState(null)

  const [pageWidth, setPageWidth] = useState(0)

  const columnRef = useRef(null)
  const busy = saving || discarding
  const { pages, failed } = loaded.url === data.previewPdfUrl ? loaded : NOT_LOADED

  useLayoutEffect(() => {
    const column = columnRef.current
    if (!column) return

    const measure = () => setPageWidth(column.clientWidth)
    measure()

    const observer = new ResizeObserver(measure)
    observer.observe(column)
    return () => observer.disconnect()
  }, [])

  const create = async () => {
    if (busy) return

    setSaving(true)
    try {
      await resumeService.create_resume(authFetch, {
        name: data.previewResume?.personal_info?.full_name ?? strings.pages.resume.untitled,
        language: resumeLanguage(),
        resume: data.previewResume,
        fileId: data.previewFileId,
        // Matching metadata, not part of the document: they feed the candidate's
        // LyNQ score. Empty when the skill extraction was never run.
        similarityTags: data.similarityTags ?? [],
      })

      updateData({ previewFileId: null })
      onCompleted?.()
    } catch (error) {
      setToast({ type: 'error', message: error.reason ?? error.message ?? t.error })
    } finally {
      setSaving(false)
    }
  }

  const discard = async () => {
    if (busy) return

    setDiscarding(true)
    if (data.previewFileId) {
      await resumeService
        .delete_resume_preview(authFetch, data.previewFileId)
        .catch(() => null)
    }
    updateData({ previewResume: null, previewFileId: null, previewPdfUrl: null })
    setDiscarding(false)
    back()
  }

  const actionsRef = useRef({ create, discard })
  useEffect(() => {
    actionsRef.current = { create, discard }
  })

  useEffect(() => {
    if (!active) return
    setFooter({
      secondary: { label: t.back, onClick: () => actionsRef.current.discard(), disabled: busy },
      primary: {
        label: t.finish,
        disabled: busy || failed,
        onClick: () => actionsRef.current.create(),
      },
    })
  }, [active, busy, failed, setFooter, t.back, t.finish])

  return (
    <div className="resume-step resume-preview-step">
      {saving && <LoadingOverlay label={t.saving} />}
      {discarding && <LoadingOverlay label={tp.discarding} />}

      <div className="resume-step-intro">
        <p className="resume-step-question">{tp.question}</p>
        <p className="resume-step-helper">{tp.helper}</p>
      </div>

      <StepIndicator
        current={stepNumber}
        total={totalSteps}
        template={t.stepCounter}
        className="step-indicator--end"
      />

      <div className="resume-preview-frame">
        <div className="resume-preview-column" ref={columnRef}>
                    {active && data.previewPdfUrl ? (
            <Document
              file={data.previewPdfUrl}
              onLoadSuccess={({ numPages }) =>
                setLoaded({ url: data.previewPdfUrl, pages: numPages, failed: false })
              }
              onLoadError={() =>
                setLoaded({ url: data.previewPdfUrl, pages: 0, failed: true })
              }
              loading={<Spinner label={tp.loading} />}
              error={<p className="resume-preview-error">{tp.loadError}</p>}
              noData={<p className="resume-preview-error">{tp.loadError}</p>}
            >
                            {Array.from({ length: pages }, (_, index) => (
                <Page
                  key={index}
                  pageNumber={index + 1}
                  width={pageWidth || undefined}
                  className="resume-preview-page"
                />
              ))}
            </Document>
          ) : null}
        </div>
      </div>

      <div className="resume-preview-meta">
        {pages > 0 && (
          <span className="resume-preview-pages">
            {tp.pageCount.replace('{count}', pages)}
          </span>
        )}
        {data.previewPdfUrl && (
          <a
            className="resume-preview-open"
            href={data.previewPdfUrl}
            target="_blank"
            rel="noreferrer"
          >
            <OpenInNewRoundedIcon sx={{ fontSize: 15 }} />
            {tp.openInTab}
          </a>
        )}
      </div>

      <Toast
        message={toast?.message}
        type={toast?.type}
        onClose={() => setToast(null)}
      />
    </div>
  )
}

export default ResumePreviewStep
