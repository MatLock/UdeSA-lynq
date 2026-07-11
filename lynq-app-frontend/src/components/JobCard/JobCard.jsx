import { Chip } from '@mui/material'
import { alpha } from '@mui/material/styles'
import strings from '../../i18n'
import UserIcon from '../UserIcon/UserIcon.jsx'
import CompanyIcon from '../CompanyIcon/CompanyIcon.jsx'
import formatRelativeDate from '../../utils/formatRelativeDate'
import './JobCard.css'

// A single available job post, laid out as a horizontal list row (see the home
// feed design): a company logo on the left, the work-type tag / title /
// description / posting meta in the middle, key skills and an Apply action on
// the right. Presentational only — the caller owns what Apply does.
const MAX_DESCRIPTION_LENGTH = 100
const MAX_SKILLS = 3
// Placeholder relevance score until the backend supplies a per-job value.
const LYNQ_SCORE = 50

// Shared MUI Chip sizing — ~20% smaller than the card's body type. Brand colors
// stay in index.css tokens (referenced here as CSS custom properties) so the
// palette remains parametrized in one place.
const CHIP_SX = {
  height: 20,
  fontSize: 9,
  fontWeight: 700,
  '& .MuiChip-label': { px: 1 },
}

// The work-type chip is filled with the matching brand color; an unknown type
// falls back to the brand blend.
const workTypeBg = (workType) =>
  workType === 'REMOTE'
    ? 'var(--brand-blue)'
    : workType === 'IN_OFFICE'
      ? 'var(--brand-purple)'
      : 'var(--brand-gradient)'

// Human-friendly names for scraped sources; unknown values pass through as-is.
const SOURCE_LABELS = {
  LINKEDIN: 'LinkedIn',
  COMPUTRABAJO: 'Computrabajo',
  BUMERAN: 'Bumeran',
}
const prettySource = (source) => SOURCE_LABELS[source] ?? source

// Clip to a preview length, appending an ellipsis only when text was actually
// dropped so short descriptions stay untouched.
const truncate = (text, max) =>
  text && text.length > max ? `${text.slice(0, max).trimEnd()}…` : text ?? ''

const JobCard = ({ job, onApply }) => {
  const t = strings.jobCard
  const workTypeLabel = t.workType[job.workType] ?? job.workType
  const skills = (job.skills ?? []).slice(0, MAX_SKILLS)
  const poster = job.postedBy
  const publishedAt = formatRelativeDate(job.createdOn)

  // The logo shows the company's profile image for LYNQ-native posts. Scraped
  // (external) posts carry no trusted company logo, so they fall back to the
  // default icon.
  const isExternal = job.jobPostSource && job.jobPostSource !== 'LYNQ'
  const logoUrl = isExternal ? null : job.company?.profileImageUrl

  return (
    <article className="job-card">
      <span className="job-card-logo">
        {logoUrl ? (
          <img src={logoUrl} alt={job.company?.name ?? t.companyLogoAlt} />
        ) : (
          <CompanyIcon />
        )}
      </span>

      <div className="job-card-main">
        <div className="job-card-tags">
          <Chip
            label={workTypeLabel}
            size="small"
            sx={{
              ...CHIP_SX,
              letterSpacing: '0.3px',
              textTransform: 'uppercase',
              color: '#fff',
              background: workTypeBg(job.workType),
            }}
          />
          <Chip
            label={`${t.lynqScore}: ${LYNQ_SCORE}`}
            size="small"
            color="warning"
            variant="outlined"
            sx={[
              CHIP_SX,
              (theme) => ({
                backgroundColor: alpha(theme.palette.warning.main, 0.12),
              }),
            ]}
          />
        </div>

        <h3 className="job-card-title">{job.title}</h3>

        <p className="job-card-description">
          {truncate(job.description, MAX_DESCRIPTION_LENGTH)}
        </p>

        {skills.length > 0 && (
          <div className="job-card-skills">
            {skills.map((skill) => (
              <Chip
                key={skill}
                label={skill}
                size="small"
                variant="outlined"
                sx={{
                  ...CHIP_SX,
                  fontWeight: 600,
                  color: 'var(--brand-purple-dark)',
                  borderColor: 'var(--accent-border)',
                  backgroundColor: 'var(--accent-bg)',
                }}
              />
            ))}
          </div>
        )}

        <div className="job-card-meta">
          {isExternal ? (
            <span
              className="job-card-source"
              title={`${t.source} ${prettySource(job.jobPostSource)}`}
            >
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2" />
                <path
                  d="M3 12h18M12 3c2.6 2.7 2.6 15.3 0 18M12 3c-2.6 2.7-2.6 15.3 0 18"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                />
              </svg>
              {prettySource(job.jobPostSource)}
            </span>
          ) : (
            <>
              <span className="job-card-poster-avatar">
                {poster?.profileImageUrl ? (
                  <img src={poster.profileImageUrl} alt={poster.fullName ?? ''} />
                ) : (
                  <UserIcon />
                )}
              </span>
              <span className="job-card-poster-name">
                {t.postedBy} {poster?.fullName ?? t.unknownPoster}
              </span>
            </>
          )}
          {publishedAt && (
            <>
              <span className="job-card-meta-sep" aria-hidden="true">
                •
              </span>
              <span className="job-card-published">
                {t.published} {publishedAt}
              </span>
            </>
          )}
        </div>
      </div>

      <div className="job-card-actions">
        <button
          type="button"
          className="job-card-apply"
          onClick={() => onApply?.(job)}
        >
          {t.apply}
        </button>
        <span className="job-card-chevron" aria-hidden="true">
          ›
        </span>
      </div>
    </article>
  )
}

export default JobCard
