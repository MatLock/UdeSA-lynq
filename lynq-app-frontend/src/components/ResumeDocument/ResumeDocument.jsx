import MailOutlineRoundedIcon from '@mui/icons-material/MailOutlineRounded'
import PhoneOutlinedIcon from '@mui/icons-material/PhoneOutlined'
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined'
import LinkedInIcon from '@mui/icons-material/LinkedIn'
import GitHubIcon from '@mui/icons-material/GitHub'
import RocketLaunchOutlinedIcon from '@mui/icons-material/RocketLaunchOutlined'
import LanguageOutlinedIcon from '@mui/icons-material/LanguageOutlined'
import LinkRoundedIcon from '@mui/icons-material/LinkRounded'
import WorkspacePremiumOutlinedIcon from '@mui/icons-material/WorkspacePremiumOutlined'
import strings from '../../i18n'
import resumeSections from '../../utils/resumeSections'
import formatResumeDate from '../../utils/formatResumeDate'
import './ResumeDocument.css'

const { hasText, entriesOf } = resumeSections
const { formatResumeDate: formatDate, formatResumeDateRange: formatRange } = formatResumeDate

// personal_info.links rendered in a fixed order, each with its own icon so the
// row reads at a glance. The label key resolves against pages.resume.labels.
const LINK_FIELDS = [
  { key: 'linkedin', label: 'linkedin', Icon: LinkedInIcon },
  { key: 'github', label: 'github', Icon: GitHubIcon },
  { key: 'portfolio', label: 'portfolio', Icon: RocketLaunchOutlinedIcon },
  { key: 'website', label: 'website', Icon: LanguageOutlinedIcon },
]

// The three skill buckets, in the order the resume JSON declares them.
const SKILL_GROUPS = [
  { key: 'technical', label: 'technical' },
  { key: 'tools', label: 'tools' },
  { key: 'soft', label: 'soft' },
]

// Up to two initials for the avatar placeholder (the resume JSON carries no
// picture, so the name stands in for one).
const initialsOf = (fullName) => {
  if (!hasText(fullName)) return '·'
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

// Read-only rendering of a resume JSON (the shape lynq-ml extracts and the
// backend stores). Every field is optional, so each block is rendered only when
// it holds content; the caller passes the already-filtered `sections` list so the
// document and the section nav can never disagree about what exists.
//
// Each section carries data-section-id, which is what useActiveSection observes
// to highlight the matching nav entry while scrolling.
const ResumeDocument = ({ resume, sections }) => {
  const t = strings.pages.resume
  const shown = new Set(sections.map((section) => section.id))

  const personalInfo = resume?.personal_info ?? {}
  const links = personalInfo.links ?? {}

  // Chip row shared by skills, languages and per-entry technology lists.
  const renderChips = (items, tone) => (
    <ul className={`resume-chips tone-${tone}`}>
      {items.map((item) => (
        <li key={item} className="resume-chip">
          {item}
        </li>
      ))}
    </ul>
  )

  // Section shell: an anchor target, a heading, and the body. `id` doubles as the
  // i18n key for the heading and as the nav's scroll target.
  const renderSection = (id, body) => (
    <section className="resume-section" data-section-id={id} key={id}>
      <h2 className="resume-section-title">{t.sections[id]}</h2>
      {body}
    </section>
  )

  const renderDates = (start, end, isCurrent) => {
    const range = formatRange(start, end, isCurrent, t.present)
    if (!range) return null
    return <span className="resume-entry-dates">{range}</span>
  }

  const renderPersonal = () => (
    <section className="resume-header" data-section-id="personal">
      <div className="resume-header-identity">
        <span className="resume-avatar" aria-hidden="true">
          {initialsOf(personalInfo.full_name)}
        </span>
        <div className="resume-header-text">
          {hasText(personalInfo.full_name) && (
            <h2 className="resume-name">{personalInfo.full_name}</h2>
          )}
          {hasText(personalInfo.headline) && (
            <p className="resume-headline">{personalInfo.headline}</p>
          )}
        </div>
      </div>

      <ul className="resume-contacts">
        {hasText(personalInfo.email) && (
          <li className="resume-contact">
            <MailOutlineRoundedIcon sx={{ fontSize: 15 }} />
            <a href={`mailto:${personalInfo.email}`}>{personalInfo.email}</a>
          </li>
        )}
        {hasText(personalInfo.phone) && (
          <li className="resume-contact">
            <PhoneOutlinedIcon sx={{ fontSize: 15 }} />
            <a href={`tel:${personalInfo.phone}`}>{personalInfo.phone}</a>
          </li>
        )}
        {hasText(personalInfo.location) && (
          <li className="resume-contact">
            <PlaceOutlinedIcon sx={{ fontSize: 15 }} />
            <span>{personalInfo.location}</span>
          </li>
        )}
      </ul>

      {LINK_FIELDS.some(({ key }) => hasText(links[key])) && (
        <ul className="resume-links">
          {LINK_FIELDS.filter(({ key }) => hasText(links[key])).map(
            ({ key, label, Icon }) => (
              <li key={key}>
                <a
                  className="resume-link"
                  href={links[key]}
                  target="_blank"
                  rel="noreferrer"
                >
                  <Icon sx={{ fontSize: 15 }} />
                  {t.labels[label]}
                </a>
              </li>
            ),
          )}
        </ul>
      )}
    </section>
  )

  const renderExperience = () =>
    renderSection(
      'experience',
      <ol className="resume-timeline">
        {entriesOf(resume.work_experience).map((job, index) => (
          <li className="resume-timeline-item" key={`${job.company}-${job.position}-${index}`}>
            <article className="resume-entry">
              <header className="resume-entry-head">
                <div>
                  <h3 className="resume-entry-title">{job.position}</h3>
                  <p className="resume-entry-meta">
                    {job.company}
                    {hasText(job.location) && <span> · {job.location}</span>}
                  </p>
                </div>
                <div className="resume-entry-side">
                  {renderDates(job.start_date, job.end_date, job.is_current)}
                  {job.is_current && (
                    <span className="resume-badge">{t.labels.current}</span>
                  )}
                </div>
              </header>

              {hasText(job.description) && (
                <p className="resume-entry-body">{job.description}</p>
              )}

              {entriesOf(job.achievements).length > 0 && (
                <div className="resume-entry-block">
                  <p className="resume-entry-block-title">{t.labels.achievements}</p>
                  <ul className="resume-bullets">
                    {entriesOf(job.achievements).map((achievement) => (
                      <li key={achievement}>{achievement}</li>
                    ))}
                  </ul>
                </div>
              )}

              {entriesOf(job.technologies).length > 0 && (
                <div className="resume-entry-block">
                  <p className="resume-entry-block-title">{t.labels.technologies}</p>
                  {renderChips(entriesOf(job.technologies), 'blue')}
                </div>
              )}
            </article>
          </li>
        ))}
      </ol>,
    )

  const renderEducation = () =>
    renderSection(
      'education',
      <ol className="resume-timeline">
        {entriesOf(resume.education).map((study, index) => (
          <li className="resume-timeline-item" key={`${study.institution}-${index}`}>
            <article className="resume-entry">
              <header className="resume-entry-head">
                <div>
                  <h3 className="resume-entry-title">
                    {[study.degree, study.field_of_study].filter(hasText).join(' · ') ||
                      study.institution}
                  </h3>
                  <p className="resume-entry-meta">{study.institution}</p>
                </div>
                <div className="resume-entry-side">
                  {renderDates(study.start_date, study.end_date, study.is_current)}
                  {study.is_current && (
                    <span className="resume-badge">{t.labels.current}</span>
                  )}
                </div>
              </header>

              {hasText(study.description) && (
                <p className="resume-entry-body">{study.description}</p>
              )}
            </article>
          </li>
        ))}
      </ol>,
    )

  const renderSkills = () =>
    renderSection(
      'skills',
      <div className="resume-skill-groups">
        {SKILL_GROUPS.filter(({ key }) => entriesOf(resume.skills?.[key]).length > 0).map(
          ({ key, label }) => (
            <div className="resume-skill-group" key={key}>
              <p className="resume-entry-block-title">{t.labels[label]}</p>
              {renderChips(entriesOf(resume.skills[key]), key === 'soft' ? 'purple' : 'blue')}
            </div>
          ),
        )}
      </div>,
    )

  const renderLanguages = () =>
    renderSection(
      'languages',
      <ul className="resume-language-list">
        {entriesOf(resume.languages).map((language, index) => (
          <li className="resume-language" key={`${language.language}-${index}`}>
            <span className="resume-language-name">{language.language}</span>
            {hasText(language.proficiency) && (
              <span className="resume-language-level">{language.proficiency}</span>
            )}
          </li>
        ))}
      </ul>,
    )

  const renderCertifications = () =>
    renderSection(
      'certifications',
      <ul className="resume-card-grid">
        {entriesOf(resume.certifications).map((certification, index) => (
          <li className="resume-card" key={`${certification.name}-${index}`}>
            <span className="resume-card-icon">
              <WorkspacePremiumOutlinedIcon sx={{ fontSize: 18 }} />
            </span>
            <div className="resume-card-body">
              <p className="resume-card-title">{certification.name}</p>
              {hasText(certification.issuer) && (
                <p className="resume-card-meta">
                  {t.labels.issuedBy} {certification.issuer}
                </p>
              )}
              {hasText(certification.issue_date) && (
                <span className="resume-entry-dates">
                  {formatDate(certification.issue_date)}
                </span>
              )}
              {hasText(certification.credential_id) && (
                <p className="resume-card-meta">
                  {t.labels.credentialId}: {certification.credential_id}
                </p>
              )}
            </div>
          </li>
        ))}
      </ul>,
    )

  const renderProjects = () =>
    renderSection(
      'projects',
      <ul className="resume-card-grid">
        {entriesOf(resume.projects).map((project, index) => (
          <li className="resume-card" key={`${project.name}-${index}`}>
            <span className="resume-card-icon">
              <RocketLaunchOutlinedIcon sx={{ fontSize: 18 }} />
            </span>
            <div className="resume-card-body">
              <p className="resume-card-title">{project.name}</p>
              {hasText(project.description) && (
                <p className="resume-card-meta">{project.description}</p>
              )}
              {entriesOf(project.technologies).length > 0 &&
                renderChips(entriesOf(project.technologies), 'blue')}
              {hasText(project.url) && (
                <a
                  className="resume-link"
                  href={project.url}
                  target="_blank"
                  rel="noreferrer"
                >
                  <LinkRoundedIcon sx={{ fontSize: 15 }} />
                  {t.labels.viewProject}
                </a>
              )}
            </div>
          </li>
        ))}
      </ul>,
    )

  return (
    <article className="resume-document">
      {shown.has('personal') && renderPersonal()}
      {shown.has('summary') &&
        renderSection('summary', <p className="resume-summary">{resume.summary}</p>)}
      {shown.has('experience') && renderExperience()}
      {shown.has('education') && renderEducation()}
      {shown.has('skills') && renderSkills()}
      {shown.has('languages') && renderLanguages()}
      {shown.has('certifications') && renderCertifications()}
      {shown.has('projects') && renderProjects()}
    </article>
  )
}

export default ResumeDocument
