import { useEffect, useRef, useState } from 'react'
import ResumeField from '../ResumeField/ResumeField'
import ResumeStepGroup from '../ResumeStepGroup/ResumeStepGroup'
import StepIndicator from '../StepIndicator/StepIndicator'
import useResumeWizard from '../../hooks/useResumeWizard'
import strings from '../../i18n'
import './ResumePersonalStep.css'

const isEmail = (value) => /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/.test(value)

// personal_info.links, rendered in the order the resume JSON declares them.
const LINK_FIELDS = ['linkedin', 'github', 'portfolio', 'website']

// Step 2 of the resume wizard: personal_info plus the resume summary — the block
// a recruiter reads first, so it leads the form path.
//
// Only the full name is required (everything else is optional in the stored
// resume JSON); the email is validated for shape when one is given.
const ResumePersonalStep = ({ active, stepNumber, totalSteps }) => {
  const t = strings.pages.resume.create
  const tp = t.personal
  const { data, updateData, next, back, setFooter } = useResumeWizard()

  const [fields, setFields] = useState(() => ({
    full_name: '',
    headline: '',
    email: '',
    phone: '',
    location: '',
    linkedin: '',
    github: '',
    portfolio: '',
    website: '',
    summary: '',
    ...data.personal,
  }))
  const [errors, setErrors] = useState({})

  const setField = (key) => (event) =>
    setFields((prev) => ({ ...prev, [key]: event.target.value }))

  const validate = () => {
    const found = {}
    if (!fields.full_name.trim()) found.full_name = tp.errors.fullNameRequired
    if (fields.email.trim() && !isEmail(fields.email.trim())) {
      found.email = tp.errors.emailInvalid
    }
    setErrors(found)
    return Object.keys(found).length === 0
  }

  const runPrimary = () => {
    if (!validate()) return
    updateData({ personal: fields })
    next()
  }

  // Keep a live reference so the footer button (registered once below) always
  // runs the latest closure with current field values.
  const primaryActionRef = useRef(runPrimary)
  useEffect(() => {
    primaryActionRef.current = runPrimary
  })

  useEffect(() => {
    if (!active) return
    setFooter({
      secondary: { label: t.back, onClick: back },
      primary: { label: t.next, onClick: () => primaryActionRef.current() },
    })
  }, [active, back, setFooter, t.back, t.next])

  return (
    <div className="resume-step resume-personal-step">
      <StepIndicator
        current={stepNumber}
        total={totalSteps}
        template={t.stepCounter}
        className="step-indicator--end"
      />

      <div className="resume-step-grid">
        <ResumeField id="resume-full-name" label={tp.fullNameLabel} error={errors.full_name}>
          <input
            id="resume-full-name"
            placeholder={tp.fullNamePlaceholder}
            value={fields.full_name}
            aria-invalid={Boolean(errors.full_name)}
            onChange={setField('full_name')}
          />
        </ResumeField>

        <ResumeField id="resume-headline" label={tp.headlineLabel}>
          <input
            id="resume-headline"
            placeholder={tp.headlinePlaceholder}
            value={fields.headline}
            onChange={setField('headline')}
          />
        </ResumeField>

        <ResumeField id="resume-email" label={tp.emailLabel} error={errors.email}>
          <input
            id="resume-email"
            type="email"
            placeholder={tp.emailPlaceholder}
            value={fields.email}
            aria-invalid={Boolean(errors.email)}
            onChange={setField('email')}
          />
        </ResumeField>

        <ResumeField id="resume-phone" label={tp.phoneLabel}>
          <input
            id="resume-phone"
            type="tel"
            placeholder={tp.phonePlaceholder}
            value={fields.phone}
            onChange={setField('phone')}
          />
        </ResumeField>

        <ResumeField id="resume-location" label={tp.locationLabel} full>
          <input
            id="resume-location"
            placeholder={tp.locationPlaceholder}
            value={fields.location}
            onChange={setField('location')}
          />
        </ResumeField>
      </div>

      <ResumeStepGroup title={tp.linksHeading}>
        <div className="resume-step-grid">
          {LINK_FIELDS.map((key) => (
            <ResumeField key={key} id={`resume-${key}`} label={tp[`${key}Label`]}>
              <input
                id={`resume-${key}`}
                type="url"
                placeholder={tp.linkPlaceholder}
                value={fields[key]}
                onChange={setField(key)}
              />
            </ResumeField>
          ))}
        </div>
      </ResumeStepGroup>

      <ResumeField id="resume-summary" label={tp.summaryLabel} full>
        <textarea
          id="resume-summary"
          className="resume-personal-summary"
          rows={4}
          placeholder={tp.summaryPlaceholder}
          value={fields.summary}
          onChange={setField('summary')}
        />
      </ResumeField>
    </div>
  )
}

export default ResumePersonalStep
