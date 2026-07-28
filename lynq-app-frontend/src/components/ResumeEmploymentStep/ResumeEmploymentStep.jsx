import { useEffect, useRef, useState } from 'react'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'
import ResumeField from '../ResumeField/ResumeField'
import ResumeEntryCard from '../ResumeEntryCard/ResumeEntryCard'
import ResumeStepGroup from '../ResumeStepGroup/ResumeStepGroup'
import MonthYearField from '../MonthYearField/MonthYearField'
import TagInput from '../TagInput/TagInput'
import StepIndicator from '../StepIndicator/StepIndicator'
import LoadingOverlay from '../LoadingOverlay/LoadingOverlay'
import Toast from '../Toast/Toast'
import useApi from '../../hooks/useApi'
import useResumeWizard from '../../hooks/useResumeWizard'
import useRotatingPhrase from '../../hooks/useRotatingPhrase'
import { EMPTY_EXPERIENCE, EMPTY_PROJECT } from '../../context/ResumeWizardContext'
import resumeService from '../../services/resumeService'
import resumeDraft from '../../utils/resumeDraft'
import strings, { activeLocale } from '../../i18n'
import './ResumeEmploymentStep.css'

const { cleanList, isBlankEntry, pruneEntries, toResumePayload } = resumeDraft

const SKILL_GROUPS = ['technical', 'tools', 'soft']

// Step 4 of the resume wizard: work experience, skills and projects — the
// professional half of the resume. It also hosts the AI skill extraction, which
// reads the draft assembled so far. The template step that follows owns the
// submit, so this step only validates and advances.
const ResumeEmploymentStep = ({ active, stepNumber, totalSteps }) => {
  const t = strings.pages.resume.create
  const tw = t.employment
  const { authFetch } = useApi()
  const { data, updateData, next, back, setFooter } = useResumeWizard()

  const [experience, setExperience] = useState(
    () => data.experience ?? [{ ...EMPTY_EXPERIENCE }],
  )
  const [skills, setSkills] = useState(
    () => data.skills ?? { technical: [], tools: [], soft: [] },
  )
  const [projects, setProjects] = useState(() => data.projects ?? [])
  const [errors, setErrors] = useState({})
  const [generating, setGenerating] = useState(false)
  const [toast, setToast] = useState(null)

  const patch = (setList) => (index, key, value) =>
    setList((prev) =>
      prev.map((entry, position) =>
        position === index ? { ...entry, [key]: value } : entry,
      ),
    )

  const patchExperience = patch(setExperience)
  const patchProject = patch(setProjects)

  const removeAt = (setList) => (index) =>
    setList((prev) => prev.filter((_, position) => position !== index))

  // Company and position both identify a job, so a started-but-incomplete entry
  // is reported rather than silently dropped. Untouched entries are ignored.
  const validate = () => {
    const jobs = {}
    experience.forEach((entry, index) => {
      if (isBlankEntry(entry)) return
      const found = {}
      if (!entry.company.trim()) found.company = tw.errors.companyRequired
      if (!entry.position.trim()) found.position = tw.errors.positionRequired
      if (Object.keys(found).length > 0) jobs[index] = found
    })

    const items = {}
    projects.forEach((entry, index) => {
      if (isBlankEntry(entry)) return
      if (!entry.name.trim()) items[index] = { name: tw.errors.projectNameRequired }
    })

    setErrors({ experience: jobs, projects: items })
    return Object.keys(jobs).length === 0 && Object.keys(items).length === 0
  }

  // Skill extraction is an LLM round-trip, so the caption cycles through a few
  // phrases instead of sitting on one — the wait reads as progress, not a stall.
  const generatingPhrase = useRotatingPhrase(tw.generatingSkills, generating)

  // The model reads the whole resume, so there has to be something in it to read:
  // a job, a study, or at least the professional summary from step 2.
  const hasSourceContent =
    pruneEntries(experience).length > 0 ||
    pruneEntries(data.education ?? []).length > 0 ||
    Boolean(data.personal?.summary?.trim())

  // Ask lynq-ml (through the backend) which skills the resume implies, then merge
  // them into the three buckets. Merging rather than replacing keeps whatever the
  // candidate typed themselves; cleanList drops duplicates case-insensitively.
  const handleGenerateSkills = async () => {
    if (!hasSourceContent || generating) return
    setGenerating(true)
    try {
      const extracted = await resumeService.extract_skills(
        authFetch,
        toResumePayload({ ...data, experience, skills, projects }),
        // The candidate is writing the resume in the UI language, so the soft
        // skills must come back in it rather than in whatever language the model
        // infers from the draft text.
        activeLocale,
      )
      const merged = {
        technical: cleanList([...skills.technical, ...(extracted?.skills ?? [])]),
        tools: cleanList([...skills.tools, ...(extracted?.tools ?? [])]),
        soft: cleanList([...skills.soft, ...(extracted?.soft ?? [])]),
      }
      setSkills(merged)

      const found = SKILL_GROUPS.reduce(
        (total, group) => total + merged[group].length,
        0,
      )
      // Nothing found isn't a failure — the resume simply had little to go on.
      if (found === 0) {
        setToast({ type: 'success', message: tw.generateSkillsEmpty })
      }
    } catch (error) {
      setToast({
        type: 'error',
        message: error.reason ?? error.message ?? tw.generateSkillsError,
      })
    } finally {
      setGenerating(false)
    }
  }

  const runPrimary = () => {
    if (!validate()) return
    updateData({ experience, skills, projects })
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
      secondary: { label: t.back, onClick: back, disabled: generating },
      primary: {
        label: t.next,
        disabled: generating,
        onClick: () => primaryActionRef.current(),
      },
    })
  }, [active, generating, back, setFooter, t.back, t.next])

  return (
    <div className="resume-step resume-employment-step">
      {/* Skill extraction takes a moment; block the page as the create-job form
          does so no other action can be triggered mid-flight. */}
      {generating && <LoadingOverlay label={generatingPhrase} />}

      <div className="resume-step-intro">
        <p className="resume-step-question">{tw.heading}</p>
        <p className="resume-step-helper">{tw.helper}</p>
      </div>

      <StepIndicator
        current={stepNumber}
        total={totalSteps}
        template={t.stepCounter}
        className="step-indicator--end"
      />

      <ResumeStepGroup
        title={tw.experienceHeading}
        addLabel={tw.addExperience}
        onAdd={() => setExperience((prev) => [...prev, { ...EMPTY_EXPERIENCE }])}
        emptyLabel={tw.experienceEmpty}
        isEmpty={experience.length === 0}
      >
        <div className="resume-step-entries">
          {experience.map((entry, index) => (
            <ResumeEntryCard
              // Index-keyed on purpose: rows have no stable id and are only ever
              // appended to or removed.
              key={`experience-${index}`}
              title={t.entry.replace('{index}', index + 1)}
              removeLabel={t.remove}
              onRemove={() => removeAt(setExperience)(index)}
            >
              <ResumeField
                id={`resume-position-${index}`}
                label={tw.positionLabel}
                error={errors.experience?.[index]?.position}
              >
                <input
                  id={`resume-position-${index}`}
                  placeholder={tw.positionPlaceholder}
                  value={entry.position}
                  aria-invalid={Boolean(errors.experience?.[index]?.position)}
                  onChange={(event) =>
                    patchExperience(index, 'position', event.target.value)
                  }
                />
              </ResumeField>

              <ResumeField
                id={`resume-company-${index}`}
                label={tw.companyLabel}
                error={errors.experience?.[index]?.company}
              >
                <input
                  id={`resume-company-${index}`}
                  placeholder={tw.companyPlaceholder}
                  value={entry.company}
                  aria-invalid={Boolean(errors.experience?.[index]?.company)}
                  onChange={(event) =>
                    patchExperience(index, 'company', event.target.value)
                  }
                />
              </ResumeField>

              <ResumeField id={`resume-job-location-${index}`} label={tw.locationLabel} full>
                <input
                  id={`resume-job-location-${index}`}
                  placeholder={tw.locationPlaceholder}
                  value={entry.location}
                  onChange={(event) =>
                    patchExperience(index, 'location', event.target.value)
                  }
                />
              </ResumeField>

              <div className="resume-step-dates">
                <ResumeField id={`resume-job-start-${index}`} label={t.dates.start}>
                  <MonthYearField
                    id={`resume-job-start-${index}`}
                    value={entry.start_date}
                    onChange={(value) => patchExperience(index, 'start_date', value)}
                  />
                </ResumeField>
                <ResumeField id={`resume-job-end-${index}`} label={t.dates.end}>
                  <MonthYearField
                    id={`resume-job-end-${index}`}
                    value={entry.end_date}
                    disabled={entry.is_current}
                    onChange={(value) => patchExperience(index, 'end_date', value)}
                  />
                </ResumeField>
              </div>

              <label className="resume-step-check">
                <input
                  type="checkbox"
                  checked={entry.is_current}
                  onChange={(event) =>
                    patchExperience(index, 'is_current', event.target.checked)
                  }
                />
                {tw.currentLabel}
              </label>

              <ResumeField
                id={`resume-job-description-${index}`}
                label={tw.descriptionLabel}
                full
              >
                <textarea
                  id={`resume-job-description-${index}`}
                  rows={3}
                  placeholder={tw.descriptionPlaceholder}
                  value={entry.description}
                  onChange={(event) =>
                    patchExperience(index, 'description', event.target.value)
                  }
                />
              </ResumeField>

              <ResumeField
                id={`resume-achievements-${index}`}
                label={tw.achievementsLabel}
                full
              >
                <TagInput
                  id={`resume-achievements-${index}`}
                  value={entry.achievements}
                  placeholder={tw.achievementsPlaceholder}
                  tone="purple"
                  onChange={(value) => patchExperience(index, 'achievements', value)}
                />
              </ResumeField>
            </ResumeEntryCard>
          ))}
        </div>
      </ResumeStepGroup>

      <ResumeStepGroup
        title={tw.skillsHeading}
        action={
          <button
            type="button"
            className="resume-employment-generate"
            onClick={handleGenerateSkills}
            disabled={!hasSourceContent || generating}
            title={hasSourceContent ? undefined : tw.generateSkillsHint}
          >
            <AutoAwesomeOutlinedIcon sx={{ fontSize: 16 }} />
            {tw.generateSkills}
          </button>
        }
      >
        <div className="resume-employment-skills">
          {/* Why the button may be unavailable — the model needs resume content to
              read before it can propose anything. */}
          {!hasSourceContent && (
            <p className="resume-employment-generate-hint">{tw.generateSkillsHint}</p>
          )}
          {SKILL_GROUPS.map((group) => (
            <ResumeField
              key={group}
              id={`resume-skills-${group}`}
              label={tw[`${group}Label`]}
            >
              <TagInput
                id={`resume-skills-${group}`}
                value={skills[group]}
                placeholder={tw[`${group}Placeholder`]}
                tone={group === 'soft' ? 'purple' : 'blue'}
                onChange={(value) =>
                  setSkills((prev) => ({ ...prev, [group]: value }))
                }
              />
            </ResumeField>
          ))}
        </div>
      </ResumeStepGroup>

      <ResumeStepGroup
        title={tw.projectsHeading}
        addLabel={tw.addProject}
        onAdd={() => setProjects((prev) => [...prev, { ...EMPTY_PROJECT }])}
        emptyLabel={tw.projectsEmpty}
        isEmpty={projects.length === 0}
      >
        <div className="resume-step-entries">
          {projects.map((entry, index) => (
            <ResumeEntryCard
              key={`project-${index}`}
              title={t.entry.replace('{index}', index + 1)}
              removeLabel={t.remove}
              onRemove={() => removeAt(setProjects)(index)}
            >
              <ResumeField
                id={`resume-project-name-${index}`}
                label={tw.projectNameLabel}
                error={errors.projects?.[index]?.name}
              >
                <input
                  id={`resume-project-name-${index}`}
                  placeholder={tw.projectNamePlaceholder}
                  value={entry.name}
                  aria-invalid={Boolean(errors.projects?.[index]?.name)}
                  onChange={(event) => patchProject(index, 'name', event.target.value)}
                />
              </ResumeField>

              <ResumeField id={`resume-project-url-${index}`} label={tw.projectUrlLabel}>
                <input
                  id={`resume-project-url-${index}`}
                  type="url"
                  placeholder={t.personal.linkPlaceholder}
                  value={entry.url}
                  onChange={(event) => patchProject(index, 'url', event.target.value)}
                />
              </ResumeField>

              <ResumeField
                id={`resume-project-description-${index}`}
                label={tw.projectDescriptionLabel}
                full
              >
                <textarea
                  id={`resume-project-description-${index}`}
                  rows={3}
                  placeholder={tw.projectDescriptionPlaceholder}
                  value={entry.description}
                  onChange={(event) =>
                    patchProject(index, 'description', event.target.value)
                  }
                />
              </ResumeField>
            </ResumeEntryCard>
          ))}
        </div>
      </ResumeStepGroup>

      <Toast
        message={toast?.message}
        type={toast?.type}
        onClose={() => setToast(null)}
      />
    </div>
  )
}

export default ResumeEmploymentStep
