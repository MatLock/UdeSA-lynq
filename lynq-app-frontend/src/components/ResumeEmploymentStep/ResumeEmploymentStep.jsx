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
import useResumeEntryList, { expandFirstInvalid } from '../../hooks/useResumeEntryList'
import useResumeWizard from '../../hooks/useResumeWizard'
import useRotatingPhrase from '../../hooks/useRotatingPhrase'
import { EMPTY_EXPERIENCE, EMPTY_PROJECT } from '../../context/ResumeWizardContext'
import resumeService from '../../services/resumeService'
import resumeDraft from '../../utils/resumeDraft'
import strings, { activeLocale } from '../../i18n'
import './ResumeEmploymentStep.css'

const { cleanList, isBlankEntry, pruneEntries, summarize, toResumePayload } = resumeDraft

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

  // Both repeatable lists are accordions: only the entry being written stays
  // open, so adding the sixth job doesn't push it below five long forms.
  const jobs = useResumeEntryList(
    data.experience ?? [{ ...EMPTY_EXPERIENCE }],
    EMPTY_EXPERIENCE,
  )
  const projects = useResumeEntryList(data.projects ?? [], EMPTY_PROJECT)
  const [skills, setSkills] = useState(
    () => data.skills ?? { technical: [], tools: [], soft: [] },
  )
  // Similarity tags the extraction returns alongside the buckets ("Asynchronous
  // Messaging" for a candidate who used RabbitMQ). They are never shown — the
  // resume does not display them — but they ship with it so the LyNQ score can
  // match this candidate against a job asking for an equivalent technology.
  // lynq-ml is Python, hence the snake_case field on the way in.
  const [similarityTags, setSimilarityTags] = useState(() => data.similarityTags ?? [])
  const [errors, setErrors] = useState({})
  const [generating, setGenerating] = useState(false)
  const [toast, setToast] = useState(null)

  // Company and position both identify a job, so a started-but-incomplete entry
  // is reported rather than silently dropped. Untouched entries are ignored.
  const validate = () => {
    const jobErrors = {}
    jobs.entries.forEach((entry, index) => {
      if (isBlankEntry(entry)) return
      const found = {}
      if (!entry.company.trim()) found.company = tw.errors.companyRequired
      if (!entry.position.trim()) found.position = tw.errors.positionRequired
      if (Object.keys(found).length > 0) jobErrors[index] = found
    })

    const projectErrors = {}
    projects.entries.forEach((entry, index) => {
      if (isBlankEntry(entry)) return
      if (!entry.name.trim()) {
        projectErrors[index] = { name: tw.errors.projectNameRequired }
      }
    })

    setErrors({ experience: jobErrors, projects: projectErrors })
    expandFirstInvalid(jobs, jobErrors)
    expandFirstInvalid(projects, projectErrors)
    return (
      Object.keys(jobErrors).length === 0 && Object.keys(projectErrors).length === 0
    )
  }

  // Skill extraction is an LLM round-trip, so the caption cycles through a few
  // phrases instead of sitting on one — the wait reads as progress, not a stall.
  const generatingPhrase = useRotatingPhrase(tw.generatingSkills, generating)

  // The model reads the whole resume, so there has to be something in it to read:
  // a job, a study, or at least the professional summary from step 2.
  const hasSourceContent =
    pruneEntries(jobs.entries).length > 0 ||
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
        toResumePayload({
          ...data,
          experience: jobs.entries,
          skills,
          projects: projects.entries,
        }),
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
      setSimilarityTags((previous) =>
        cleanList([...previous, ...(extracted?.similarity_tags ?? [])]),
      )

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
    updateData({
      experience: jobs.entries,
      skills,
      similarityTags,
      projects: projects.entries,
    })
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
        onAdd={jobs.add}
        emptyLabel={tw.experienceEmpty}
        isEmpty={jobs.entries.length === 0}
      >
        <div className="resume-step-entries">
          {jobs.entries.map((entry, index) => (
            <ResumeEntryCard
              // Index-keyed on purpose: rows have no stable id and are only ever
              // appended to or removed.
              key={`experience-${index}`}
              title={t.entry.replace('{index}', index + 1)}
              summary={summarize(entry.position, entry.company)}
              removeLabel={t.remove}
              toggleLabel={t.toggleEntry}
              invalidLabel={t.entryIncomplete}
              expanded={jobs.expanded === index}
              invalid={Boolean(errors.experience?.[index])}
              onToggle={() => jobs.toggle(index)}
              onRemove={() => jobs.remove(index)}
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
                    jobs.patch(index, 'position', event.target.value)
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
                    jobs.patch(index, 'company', event.target.value)
                  }
                />
              </ResumeField>

              <ResumeField id={`resume-job-location-${index}`} label={tw.locationLabel} full>
                <input
                  id={`resume-job-location-${index}`}
                  placeholder={tw.locationPlaceholder}
                  value={entry.location}
                  onChange={(event) =>
                    jobs.patch(index, 'location', event.target.value)
                  }
                />
              </ResumeField>

              <div className="resume-step-dates">
                <ResumeField id={`resume-job-start-${index}`} label={t.dates.start}>
                  <MonthYearField
                    id={`resume-job-start-${index}`}
                    value={entry.start_date}
                    onChange={(value) => jobs.patch(index, 'start_date', value)}
                  />
                </ResumeField>
                <ResumeField id={`resume-job-end-${index}`} label={t.dates.end}>
                  <MonthYearField
                    id={`resume-job-end-${index}`}
                    value={entry.end_date}
                    disabled={entry.is_current}
                    onChange={(value) => jobs.patch(index, 'end_date', value)}
                  />
                </ResumeField>
              </div>

              <label className="resume-step-check">
                <input
                  type="checkbox"
                  checked={entry.is_current}
                  onChange={(event) =>
                    jobs.patch(index, 'is_current', event.target.checked)
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
                    jobs.patch(index, 'description', event.target.value)
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
                  onChange={(value) => jobs.patch(index, 'achievements', value)}
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
        onAdd={projects.add}
        emptyLabel={tw.projectsEmpty}
        isEmpty={projects.entries.length === 0}
      >
        <div className="resume-step-entries">
          {projects.entries.map((entry, index) => (
            <ResumeEntryCard
              key={`project-${index}`}
              title={t.entry.replace('{index}', index + 1)}
              summary={summarize(entry.name, entry.url)}
              removeLabel={t.remove}
              toggleLabel={t.toggleEntry}
              invalidLabel={t.entryIncomplete}
              expanded={projects.expanded === index}
              invalid={Boolean(errors.projects?.[index])}
              onToggle={() => projects.toggle(index)}
              onRemove={() => projects.remove(index)}
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
                  onChange={(event) => projects.patch(index, 'name', event.target.value)}
                />
              </ResumeField>

              <ResumeField id={`resume-project-url-${index}`} label={tw.projectUrlLabel}>
                <input
                  id={`resume-project-url-${index}`}
                  type="url"
                  placeholder={t.personal.linkPlaceholder}
                  value={entry.url}
                  onChange={(event) => projects.patch(index, 'url', event.target.value)}
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
                    projects.patch(index, 'description', event.target.value)
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
