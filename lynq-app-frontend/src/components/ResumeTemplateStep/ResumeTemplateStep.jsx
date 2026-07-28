import { useEffect, useRef, useState } from 'react'
import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined'
import ViewSidebarOutlinedIcon from '@mui/icons-material/ViewSidebarOutlined'
import LoadingOverlay from '../LoadingOverlay/LoadingOverlay'
import ResumeOptionCards from '../ResumeOptionCards/ResumeOptionCards'
import StepIndicator from '../StepIndicator/StepIndicator'
import Toast from '../Toast/Toast'
import useApi from '../../hooks/useApi'
import useResumeWizard from '../../hooks/useResumeWizard'
import resumeService from '../../services/resumeService'
import resumeDraft from '../../utils/resumeDraft'
import strings, { activeLocale } from '../../i18n'
import './ResumeTemplateStep.css'

// Templates lynq-ml can render (model/resume_template.py Template enum), whose
// default is MODERN — so that is what this step opens on.
const DEFAULT_TEMPLATE = 'MODERN'

// The language the resume content is written in is the language the candidate
// filled the form in — i.e. the configured UI locale. Anything the backend does
// not store (Language enum) falls back to English.
const resumeLanguage = () => {
  const code = activeLocale.toUpperCase()
  return resumeService.LANGUAGES.includes(code) ? code : 'EN'
}

// Last step of the resume wizard: which visual template the PDF is built with.
//
// Being the final step it also owns the submit — it merges everything the earlier
// steps stashed in the wizard context, converts that draft into the resume JSON,
// and sends it with the chosen template as one create call.
const ResumeTemplateStep = ({ active, stepNumber, totalSteps, onCompleted }) => {
  const t = strings.pages.resume.create
  const tt = t.template
  const { authFetch } = useApi()
  const { data, updateData, back, setFooter } = useResumeWizard()

  const [template, setTemplate] = useState(data.template ?? DEFAULT_TEMPLATE)
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState(null)

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

  const submit = async () => {
    if (saving) return

    const draft = { ...data, template }
    const resume = resumeDraft.toResumePayload(draft)

    setSaving(true)
    try {
      await resumeService.create_resume(authFetch, {
        name: resume.personal_info.full_name ?? strings.pages.resume.untitled,
        language: resumeLanguage(),
        template,
        resume,
      })
      // Keep the choice in context so a failed retry doesn't lose it; the parent
      // unmounts the wizard on success anyway.
      updateData({ template })
      onCompleted?.('form')
    } catch (error) {
      setToast({ type: 'error', message: error.reason ?? error.message ?? t.error })
    } finally {
      setSaving(false)
    }
  }

  // Keep a live reference so the footer button (registered once below) always runs
  // the latest closure with the current selection.
  const primaryActionRef = useRef(submit)
  useEffect(() => {
    primaryActionRef.current = submit
  })

  useEffect(() => {
    if (!active) return
    setFooter({
      secondary: { label: t.back, onClick: back, disabled: saving },
      primary: {
        label: t.finish,
        disabled: saving,
        onClick: () => primaryActionRef.current(),
      },
    })
  }, [active, saving, back, setFooter, t.back, t.finish])

  return (
    <div className="resume-step resume-template-step">
      {saving && <LoadingOverlay label={t.saving} />}

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
