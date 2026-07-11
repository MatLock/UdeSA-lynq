import { useState } from 'react'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined'
import SellOutlinedIcon from '@mui/icons-material/SellOutlined'
import jobService from '../../services/jobService'
import strings from '../../i18n'
import './SkillsField.css'

// AI-assisted skills editor for the create-job form. The left panel generates
// suggested skills from the job's title/description; the right panel is the
// editable list the company owner curates before posting. Skills live in the
// parent (they ship with the job), so this component is controlled via
// `skills` + `onChange`; the in-progress "add" text and generating flag are
// local since they never leave this widget.
const SkillsField = ({
  title,
  description,
  workType,
  skills,
  onChange,
  authFetch,
  onError,
}) => {
  const t = strings.pages.createJob
  const [skillInput, setSkillInput] = useState('')
  const [generating, setGenerating] = useState(false)

  // Skills are generated from the title + description, so only enable it once
  // both are filled in.
  const canGenerate = Boolean(title.trim() && description.trim())

  // Append new skills, keeping order and dropping blanks / case-insensitive
  // duplicates (mirrors how the feed renders skill chips).
  const mergeSkills = (incoming) => {
    const seen = new Set(skills.map((skill) => skill.toLowerCase()))
    const out = [...skills]
    for (const token of incoming) {
      const skill = token.trim()
      if (skill && !seen.has(skill.toLowerCase())) {
        seen.add(skill.toLowerCase())
        out.push(skill)
      }
    }
    return out
  }

  const removeSkill = (target) => {
    onChange(skills.filter((skill) => skill !== target))
  }

  // Commit whatever is in the add-input (supports comma-separated entry).
  const addTypedSkills = () => {
    if (!skillInput.trim()) return
    onChange(mergeSkills(skillInput.split(',')))
    setSkillInput('')
  }

  const handleInputKeyDown = (event) => {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      addTypedSkills()
    }
  }

  const handleGenerate = async () => {
    if (!canGenerate || generating) return
    setGenerating(true)
    try {
      const generated = await jobService.generate_skills(authFetch, {
        title: title.trim(),
        description: description.trim(),
        workType: workType || undefined,
      })
      onChange(mergeSkills(generated))
    } catch (error) {
      onError?.(error.reason ?? error.message ?? t.generateError)
    } finally {
      setGenerating(false)
    }
  }

  return (
    <section className="skills-field">
      <header className="skills-field-header">
        <span className="skills-field-header-icon">
          <AutoAwesomeOutlinedIcon sx={{ fontSize: 18 }} />
        </span>
        <div>
          <h2 className="skills-field-heading">{t.skillsLabel}</h2>
          <p className="skills-field-subtitle">{t.skillsNote}</p>
        </div>
      </header>

      <div className="skills-field-grid">
        {/* Left: AI generation. */}
        <div className="skills-field-panel skills-field-generate-panel">
          <div className="skills-field-panel-head">
            <span className="skills-field-panel-title">{t.aiSkillsTitle}</span>
          </div>

          <p className="skills-field-hint">
            {canGenerate ? t.skillsEmpty : t.generateHint}
          </p>

          <button
            type="button"
            className="skills-field-generate"
            onClick={handleGenerate}
            disabled={!canGenerate || generating}
            title={canGenerate ? undefined : t.generateHint}
          >
            <AutoAwesomeOutlinedIcon sx={{ fontSize: 16 }} />
            {generating ? t.generating : skills.length > 0 ? t.regenerate : t.generate}
          </button>
        </div>

        {/* Right: editable list. */}
        <div className="skills-field-panel">
          {skills.length > 0 && (
            <div className="skills-field-panel-head">
              <span className="skills-field-count">{skills.length}</span>
            </div>
          )}

          <div
            className={`skills-field-box${
              skills.length === 0 ? ' skills-field-box--empty' : ''
            }`}
          >
            {skills.length === 0 ? (
              <div className="skills-field-empty">
                <span className="skills-field-empty-icon">
                  <SellOutlinedIcon sx={{ fontSize: 18 }} />
                </span>
                <p className="skills-field-empty-title">{t.skillsBoxEmptyTitle}</p>
                <p className="skills-field-empty-subtitle">{t.skillsBoxEmptySubtitle}</p>
              </div>
            ) : (
              skills.map((skill) => (
                <span key={skill} className="skills-field-chip">
                  {skill}
                  <button
                    type="button"
                    className="skills-field-chip-remove"
                    aria-label={`${t.removeSkill}: ${skill}`}
                    onClick={() => removeSkill(skill)}
                  >
                    <CloseOutlinedIcon sx={{ fontSize: 13 }} />
                  </button>
                </span>
              ))
            )}
          </div>

          <label className="skills-field-add-label" htmlFor="create-job-skills">
            {t.addYourOwnLabel}
          </label>
          <div className="skills-field-add">
            <input
              id="create-job-skills"
              className="skills-field-input"
              placeholder={t.skillsPlaceholder}
              value={skillInput}
              onChange={(event) => setSkillInput(event.target.value)}
              onKeyDown={handleInputKeyDown}
              onBlur={addTypedSkills}
            />
            <button
              type="button"
              className="skills-field-add-button"
              onClick={addTypedSkills}
              disabled={!skillInput.trim()}
            >
              {t.addSkill}
            </button>
          </div>
        </div>
      </div>
    </section>
  )
}

export default SkillsField
