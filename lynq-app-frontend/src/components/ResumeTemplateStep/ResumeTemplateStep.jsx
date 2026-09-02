import { useEffect, useRef, useState } from 'react'
import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined'
import ViewSidebarOutlinedIcon from '@mui/icons-material/ViewSidebarOutlined'
import LoadingOverlay from '../LoadingOverlay/LoadingOverlay'
import ResumeOptionCards from '../ResumeOptionCards/ResumeOptionCards'
import StepIndicator from '../StepIndicator/StepIndicator'
import Toast from '../Toast/Toast'
import useApi from '../../hooks/useApi'
import useResumeWizard from '../../hooks/useResumeWizard'
import useRotatingPhrase from '../../hooks/useRotatingPhrase'
import resumeService from '../../services/resumeService'
import resumeDraft from '../../utils/resumeDraft'
import strings from '../../i18n'
import './ResumeTemplateStep.css'

const DEFAULT_TEMPLATE = 'MODERN'

const ResumeTemplateStep = ({ active, stepNumber, totalSteps }) => {
  const t = strings.pages.resume.create
  const tt = t.template
  const { authFetch } = useApi()
  const { data, updateData, back, next, setFooter } = useResumeWizard()

  const [template, setTemplate] = useState(data.template ?? DEFAULT_TEMPLATE)
  const [rendering, setRendering] = useState(false)
  const [toast, setToast] = useState(null)

  const renderingPhrase = useRotatingPhrase(tt.rendering, rendering)

  const options = [
    {
      value: 'CLASSIC',
      tone: 'purple',
      title: tt.classic,
      description: tt.classicDesc,
      Icon: ArticleOutlinedIcon,
    },
    {
      value: 'MODERN',
      tone: 'blue',
      title: tt.modern,
      description: tt.modernDesc,
      Icon: ViewSidebarOutlinedIcon,
    },
  ]

  const render = async () => {
    if (rendering) return

    const resume = resumeDraft.toResumePayload({ ...data, template })

    setRendering(true)
    try {
      const preview = await resumeService.preview_resume(authFetch, { resume, template })

      updateData({
        template,
        previewResume: resume,
        previewFileId: preview.fileId,
        previewPdfUrl: preview.pdfUrl,
      })
      next()
    } catch (error) {
      setToast({ type: 'error', message: error.reason ?? error.message ?? tt.renderError })
    } finally {
      setRendering(false)
    }
  }

  const primaryActionRef = useRef(render)
  useEffect(() => {
    primaryActionRef.current = render
  })

  useEffect(() => {
    if (!active) return
    setFooter({
      secondary: { label: t.back, onClick: back, disabled: rendering },
      primary: {
        label: t.next,
        disabled: rendering,
        onClick: () => primaryActionRef.current(),
      },
    })
  }, [active, rendering, back, setFooter, t.back, t.next])

  return (
    <div className="resume-step resume-template-step">
      {rendering && <LoadingOverlay label={renderingPhrase} />}

      <div className="resume-step-intro">
        <p className="resume-step-question">{tt.question}</p>
        <p className="resume-step-helper">{tt.helper}</p>
      </div>

      <StepIndicator
        current={stepNumber}
        total={totalSteps}
        template={t.stepCounter}
        className="step-indicator--end"
      />

      <ResumeOptionCards
        name="resumeTemplate"
        value={template}
        options={options}
        onChange={setTemplate}
      />

      <Toast
        message={toast?.message}
        type={toast?.type}
        onClose={() => setToast(null)}
      />
    </div>
  )
}

export default ResumeTemplateStep
