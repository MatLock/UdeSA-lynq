import { useState } from 'react'
import { Chip } from '@mui/material'
import { Link } from 'react-router-dom'
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

// Map a 0–100 LYNQ score to its band color token (see index.css --score-*):
// 0–20 red · 21–40 orange · 41–60 yellow · 61–80 light green · 81–100 green.
const scoreColorVar = (score) => {
  if (score <= 20) return '--score-red'
  if (score <= 40) return '--score-orange'
  if (score <= 60) return '--score-yellow'
  if (score <= 80) return '--score-light-green'
  return '--score-green'
}

// Clip to a preview length, appending an ellipsis only when text was actually
// dropped so short descriptions stay untouched.
const truncate = (text, max) =>
  text && text.length > max ? `${text.slice(0, max).trimEnd()}…` : text ?? ''

const JobCard = ({
  job,
  onApply,
  showScore = true,
  showStatus = false,
  showCandidates = false,
  actions,
}) => {
  const t = strings.jobCard
  const workTypeLabel = t.workType[job.workType] ?? job.workType
  // Owner-facing lists (my job posts) surface the post's lifecycle state; the
  // public feed never does since it only ever shows OPEN posts.
  const isClosed = job.jobStatus === 'CLOSE'
  // The backend supplies a per-job relevance score (0–100), null when it can't
  // compute one (e.g. company users, or a candidate with no matching skills).
  const hasScore = showScore && job.lynqScore != null
  // Skills are capped at MAX_SKILLS with a "…" chip that reveals the rest in
  // place; expansion is per-card local state since it never leaves this widget.
  const [showAllSkills, setShowAllSkills] = useState(false)
  const allSkills = job.skills ?? []
  const hasMoreSkills = allSkills.length > MAX_SKILLS
  const skills = showAllSkills ? allSkills : allSkills.slice(0, MAX_SKILLS)
  const poster = job.postedBy
  const publishedAt = formatRelativeDate(job.createdOn)

  // The logo shows the company's profile image for LYNQ-native posts. Scraped
  // (external) posts carry no trusted company logo, so they fall back to the
  // default icon.
  const isExternal = job.jobPostSource && job.jobPostSource !== 'LYNQ'
  const logoUrl = isExternal ? null : job.company?.profileImageUrl

  return (
    <article className={`job-card${showStatus && isClosed ? ' is-closed' : ''}`}>
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
          {/* The LYNQ score is a candidate-facing relevance hint (hidden from
              company users and when unscored). Its color follows the 0–100 band. */}
          {hasScore && (
            <Chip
              label={`${t.lynqScore}: ${job.lynqScore}`}
              size="small"
              variant="outlined"
              className="job-card-score-chip"
              sx={{
                ...CHIP_SX,
                fontWeight: 700,
                color: `var(${scoreColorVar(job.lynqScore)})`,
                borderColor: `var(${scoreColorVar(job.lynqScore)})`,
                backgroundColor: `color-mix(in srgb, var(${scoreColorVar(job.lynqScore)}) 14%, transparent)`,
              }}
            />
          )}
          {/* Lifecycle badge for owner lists: green when live, muted red when
              closed. Pinned right unless the score chip already claimed that. */}
          {showStatus && (
            <Chip
              label={isClosed ? t.status.CLOSE : t.status.OPEN}
              size="small"
              variant="outlined"
              className={hasScore ? undefined : 'job-card-score-chip'}
              sx={{
                ...CHIP_SX,
                fontWeight: 700,
                color: isClosed ? 'var(--score-red)' : 'var(--score-green)',
                borderColor: isClosed ? 'var(--score-red)' : 'var(--score-green)',
                backgroundColor: isClosed
                  ? 'color-mix(in srgb, var(--score-red) 14%, transparent)'
                  : 'color-mix(in srgb, var(--score-green) 14%, transparent)',
              }}
            />
          )}
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
            {hasMoreSkills && !showAllSkills && (
              <Chip
                label="…"
                size="small"
                variant="outlined"
                clickable
                onClick={() => setShowAllSkills(true)}
                aria-label={t.showAllSkills}
                title={t.showAllSkills}
                sx={{
                  ...CHIP_SX,
                  fontWeight: 700,
                  color: 'var(--brand-purple-dark)',
                  borderColor: 'var(--accent-border)',
                  backgroundColor: 'var(--accent-bg)',
                }}
              />
            )}
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
                <span className="job-card-poster-label">{t.postedBy}</span>
                {/* The name links to the poster's user detail page (created
                    later) — not the signed-in user's own /profile. Falls back to
                    plain text when the post has no identified poster. */}
                {poster?.id ? (
                  <Link to={`/user/${poster.id}`} className="job-card-poster-value">
                    {poster.fullName ?? t.unknownPoster}
                  </Link>
                ) : (
                  <span className="job-card-poster-value">
                    {poster?.fullName ?? t.unknownPoster}
                  </span>
                )}
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
          {/* Owner lists (my job posts) pin the applicant tally and a shortcut to
              the candidates page at the right of the footer. */}
          {showCandidates && (
            <span className="job-card-candidates">
              <span className="job-card-candidates-count">
                {t.candidatesApplied.replace('{count}', job.totalCandidatesApplied ?? 0)}
              </span>
              <Link
                to={`/job/${job.jobId}/candidates`}
                state={{ job }}
                className="job-card-candidates-link"
              >
                {t.seeCandidates}
                <span aria-hidden="true"> ›</span>
              </Link>
            </span>
          )}
        </div>
      </div>

      {/* Callers can override the trailing action (e.g. the owner list swaps
          "See details" for an Edit button). By default, "See details" navigates
          to the job's detail page, handing the already loaded job object along
          via router state so the detail page can render instantly without a
          redundant fetch. onApply, when provided, still fires for callers that
          want to observe the click. */}
      {actions ?? (
        <Link
          to={`/job/${job.jobId}/details`}
          state={{ job }}
          className="job-card-actions"
          aria-label={`${t.apply} — ${job.title}`}
          onClick={() => onApply?.(job)}
        >
          <span className="job-card-apply">{t.apply}</span>
          <span className="job-card-chevron" aria-hidden="true">
            ›
          </span>
        </Link>
      )}
    </article>
  )
}

export default JobCard
