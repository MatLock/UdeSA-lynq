import { useEffect, useRef, useState } from 'react'
import ResumeField from '../ResumeField/ResumeField'
import ResumeEntryCard from '../ResumeEntryCard/ResumeEntryCard'
import ResumeStepGroup from '../ResumeStepGroup/ResumeStepGroup'
import MonthYearField from '../MonthYearField/MonthYearField'
import StepIndicator from '../StepIndicator/StepIndicator'
import useResumeWizard from '../../hooks/useResumeWizard'
import {
  EMPTY_CERTIFICATION,
  EMPTY_EDUCATION,
  EMPTY_LANGUAGE,
} from '../../context/ResumeWizardContext'
import resumeDraft from '../../utils/resumeDraft'
import strings from '../../i18n'
import './ResumeEducationStep.css'

const { isBlankEntry } = resumeDraft

// Step 3 of the resume wizard: everything the candidate studied or was awarded —
// education entries, spoken languages and certifications. Grouping the three here
// keeps the wizard at the four views the flow calls for while still collecting
// every field the stored resume JSON has.
//
// All three lists are repeatable and start empty except education, which opens
// with one card so the step is never a bare "add" button. Entries the user adds
// and leaves untouched are dropped instead of failing validation.
const ResumeEducationStep = ({ active, stepNumber, totalSteps }) => {
  const t = strings.pages.resume.create
  const te = t.education
  const { data, updateData, next, back, setFooter } = useResumeWizard()

  const [education, setEducation] = useState(
    () => data.education ?? [{ ...EMPTY_EDUCATION }],
  )
  const [languages, setLanguages] = useState(() => data.languages ?? [])
  const [certifications, setCertifications] = useState(() => data.certifications ?? [])
  // Per-list validation errors, keyed by entry index: { education: {0: {…}}, … }
  const [errors, setErrors] = useState({})

  // Immutably patch one entry of one list.
  const patch = (setList) => (index, key, value) =>
    setList((prev) =>
      prev.map((entry, position) =>
        position === index ? { ...entry, [key]: value } : entry,
      ),
    )

  const patchEducation = patch(setEducation)
  const patchLanguage = patch(setLanguages)
  const patchCertification = patch(setCertifications)

  const removeAt = (setList) => (index) =>
    setList((prev) => prev.filter((_, position) => position !== index))

  // Require the one field that identifies an entry, but only for entries the user
  // actually started filling in.
  const validateList = (entries, requiredKey, message) => {
    const found = {}
    entries.forEach((entry, index) => {
      if (isBlankEntry(entry)) return
      if (!String(entry[requiredKey] ?? '').trim()) {
        found[index] = { [requiredKey]: message }
      }
    })
    return found
  }

  const runPrimary = () => {
    const found = {
      education: validateList(education, 'institution', te.errors.institutionRequired),
      languages: validateList(languages, 'language', te.errors.languageRequired),
      certifications: validateList(
        certifications,
        'name',
        te.errors.certificationRequired,
      ),
    }
    setErrors(found)
    if (Object.values(found).some((list) => Object.keys(list).length > 0)) return

    updateData({ education, languages, certifications })
    next()
  }

  // Keep a live reference so the footer button (registered once below) always
  // runs the latest closure with current entry values.
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

  // "From"/"To" pair shared by the education cards. A study still in progress has
  // no end date, so ticking "I still study here" disables that side.
  const renderDates = (entry, index, onPatch) => (
    <>
      <div className="resume-step-dates">
        <ResumeField id={`resume-study-start-${index}`} label={t.dates.start}>
          <MonthYearField
            id={`resume-study-start-${index}`}
            value={entry.start_date}
            onChange={(value) => onPatch(index, 'start_date', value)}
          />
        </ResumeField>
        <ResumeField id={`resume-study-end-${index}`} label={t.dates.end}>
          <MonthYearField
            id={`resume-study-end-${index}`}
            value={entry.end_date}
            disabled={entry.is_current}
            onChange={(value) => onPatch(index, 'end_date', value)}
          />
        </ResumeField>
      </div>
      <label className="resume-step-check">
        <input
          type="checkbox"
          checked={entry.is_current}
          onChange={(event) => onPatch(index, 'is_current', event.target.checked)}
        />
        {te.currentLabel}
      </label>
    </>
  )

  return (
    <div className="resume-step resume-education-step">
      <div className="resume-step-intro">
        <p className="resume-step-question">{te.heading}</p>
        <p className="resume-step-helper">{te.helper}</p>
      </div>

      <StepIndicator
        current={stepNumber}
        total={totalSteps}
        template={t.stepCounter}
        className="step-indicator--end"
      />

      <ResumeStepGroup
        title={te.studiesHeading}
        addLabel={te.addEducation}
        onAdd={() => setEducation((prev) => [...prev, { ...EMPTY_EDUCATION }])}
        emptyLabel={te.educationEmpty}
        isEmpty={education.length === 0}
      >
        <div className="resume-step-entries">
          {education.map((entry, index) => (
            <ResumeEntryCard
              // Index-keyed on purpose: these rows have no stable id and are only
              // ever appended to or removed, so React's reconciliation is correct.
              key={`education-${index}`}
              title={t.entry.replace('{index}', index + 1)}
              removeLabel={t.remove}
              onRemove={() => removeAt(setEducation)(index)}
            >
              <ResumeField
                id={`resume-institution-${index}`}
                label={te.institutionLabel}
                error={errors.education?.[index]?.institution}
                full
              >
                <input
                  id={`resume-institution-${index}`}
                  placeholder={te.institutionPlaceholder}
                  value={entry.institution}
                  aria-invalid={Boolean(errors.education?.[index]?.institution)}
                  onChange={(event) =>
                    patchEducation(index, 'institution', event.target.value)
                  }
                />
              </ResumeField>

              <ResumeField id={`resume-degree-${index}`} label={te.degreeLabel}>
                <input
                  id={`resume-degree-${index}`}
                  placeholder={te.degreePlaceholder}
                  value={entry.degree}
                  onChange={(event) => patchEducation(index, 'degree', event.target.value)}
                />
              </ResumeField>

              <ResumeField id={`resume-field-${index}`} label={te.fieldLabel}>
                <input
                  id={`resume-field-${index}`}
                  placeholder={te.fieldPlaceholder}
                  value={entry.field_of_study}
                  onChange={(event) =>
                    patchEducation(index, 'field_of_study', event.target.value)
                  }
                />
              </ResumeField>

              {renderDates(entry, index, patchEducation)}

              <ResumeField
                id={`resume-study-description-${index}`}
                label={te.descriptionLabel}
                full
              >
                <textarea
                  id={`resume-study-description-${index}`}
                  rows={3}
                  placeholder={te.descriptionPlaceholder}
                  value={entry.description}
                  onChange={(event) =>
                    patchEducation(index, 'description', event.target.value)
                  }
                />
              </ResumeField>
            </ResumeEntryCard>
          ))}
        </div>
      </ResumeStepGroup>

      <ResumeStepGroup
        title={te.languagesHeading}
        addLabel={te.addLanguage}
        onAdd={() => setLanguages((prev) => [...prev, { ...EMPTY_LANGUAGE }])}
        emptyLabel={te.languagesEmpty}
        isEmpty={languages.length === 0}
      >
        <div className="resume-step-entries">
          {languages.map((entry, index) => (
            <ResumeEntryCard
              key={`language-${index}`}
              title={t.entry.replace('{index}', index + 1)}
              removeLabel={t.remove}
              onRemove={() => removeAt(setLanguages)(index)}
            >
              <ResumeField
                id={`resume-language-${index}`}
                label={te.languageLabel}
                error={errors.languages?.[index]?.language}
              >
                <input
                  id={`resume-language-${index}`}
                  placeholder={te.languagePlaceholder}
                  value={entry.language}
                  aria-invalid={Boolean(errors.languages?.[index]?.language)}
                  onChange={(event) =>
                    patchLanguage(index, 'language', event.target.value)
                  }
                />
              </ResumeField>

              <ResumeField id={`resume-proficiency-${index}`} label={te.proficiencyLabel}>
                <input
                  id={`resume-proficiency-${index}`}
                  placeholder={te.proficiencyPlaceholder}
                  value={entry.proficiency}
                  onChange={(event) =>
                    patchLanguage(index, 'proficiency', event.target.value)
                  }
                />
              </ResumeField>
            </ResumeEntryCard>
          ))}
        </div>
      </ResumeStepGroup>

      <ResumeStepGroup
        title={te.certificationsHeading}
        addLabel={te.addCertification}
        onAdd={() => setCertifications((prev) => [...prev, { ...EMPTY_CERTIFICATION }])}
        emptyLabel={te.certificationsEmpty}
        isEmpty={certifications.length === 0}
      >
        <div className="resume-step-entries">
          {certifications.map((entry, index) => (
            <ResumeEntryCard
              key={`certification-${index}`}
              title={t.entry.replace('{index}', index + 1)}
              removeLabel={t.remove}
              onRemove={() => removeAt(setCertifications)(index)}
            >
              <ResumeField
                id={`resume-certification-${index}`}
                label={te.certificationLabel}
                error={errors.certifications?.[index]?.name}
                full
              >
                <input
                  id={`resume-certification-${index}`}
                  placeholder={te.certificationPlaceholder}
                  value={entry.name}
                  aria-invalid={Boolean(errors.certifications?.[index]?.name)}
                  onChange={(event) =>
                    patchCertification(index, 'name', event.target.value)
                  }
                />
              </ResumeField>

              <ResumeField id={`resume-issuer-${index}`} label={te.issuerLabel}>
                <input
                  id={`resume-issuer-${index}`}
                  placeholder={te.issuerPlaceholder}
                  value={entry.issuer}
                  onChange={(event) =>
                    patchCertification(index, 'issuer', event.target.value)
                  }
                />
              </ResumeField>

              <ResumeField id={`resume-issue-date-${index}`} label={te.issueDateLabel}>
                <MonthYearField
                  id={`resume-issue-date-${index}`}
                  value={entry.issue_date}
                  onChange={(value) => patchCertification(index, 'issue_date', value)}
                />
              </ResumeField>

              <ResumeField
                id={`resume-credential-${index}`}
                label={te.credentialIdLabel}
              >
                <input
                  id={`resume-credential-${index}`}
                  placeholder={te.credentialIdPlaceholder}
                  value={entry.credential_id}
                  onChange={(event) =>
                    patchCertification(index, 'credential_id', event.target.value)
                  }
                />
              </ResumeField>
            </ResumeEntryCard>
          ))}
        </div>
      </ResumeStepGroup>
    </div>
  )
}

export default ResumeEducationStep
