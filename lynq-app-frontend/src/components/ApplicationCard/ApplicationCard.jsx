import { Chip } from '@mui/material'
import { Link } from 'react-router-dom'
import strings from '../../i18n'
import CompanyIcon from '../CompanyIcon/CompanyIcon.jsx'
import formatRelativeDate from '../../utils/formatRelativeDate'
import './ApplicationCard.css'

// A single job the signed-in candidate applied to, laid out as a horizontal list
// row that mirrors the JobCard language (company logo left · title/company/meta
// middle · LYNQ score + "See details" right). Fed by UserApplicationResponse
// (see userService.get_user_applications), whose shape differs from a job post:
// it carries no work type, skills or poster — only the job, its owning company's
// public fields, the application date and the candidate's LYNQ score. Scraped
// jobs have no company, so the logo/name fall back to neutral defaults.
// Presentational only.
const MAX_DESCRIPTION_LENGTH = 220

const CHIP_SX = {
  height: 20,
  fontSize: 9,
  fontWeight: 700,
  '& .MuiChip-label': { px: 1 },
}

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

const ApplicationCard = ({ application }) => {
  const t = strings.pages.applications
  const hasScore = application.lynqScore != null
  const appliedAt = formatRelativeDate(application.appliedOn)
  const companyName = application.companyName ?? t.externalCompany
  const logoUrl = application.companyProfileImage

  return (
    <article className="application-card">
      <span className="application-card-logo">
        {logoUrl ? (
          <img src={logoUrl} alt={companyName} />
        ) : (
          <CompanyIcon />
        )}
      </span>

      <div className="application-card-main">
        <div className="application-card-heading">
          {application.companyId ? (
            <Link
              to={`/company/${application.companyId}`}
              className="application-card-company"
            >
              {companyName}
            </Link>
          ) : (
            <span className="application-card-company is-external">{companyName}</span>
          )}
          {appliedAt && (
            <>
              <span className="application-card-meta-sep" aria-hidden="true">
                •
              </span>
              <span className="application-card-applied">
                {t.appliedOn} {appliedAt}
              </span>
            </>
          )}
        </div>

        <h3 className="application-card-title">{application.jobTitle}</h3>

        <p className="application-card-description">
          {truncate(application.jobDescription, MAX_DESCRIPTION_LENGTH)}
        </p>
      </div>

      <div className="application-card-side">
        {hasScore && (
          <Chip
            label={`${t.lynqScore}: ${application.lynqScore}`}
            size="small"
            variant="outlined"
            sx={{
              ...CHIP_SX,
              color: `var(${scoreColorVar(application.lynqScore)})`,
              borderColor: `var(${scoreColorVar(application.lynqScore)})`,
              backgroundColor: `color-mix(in srgb, var(${scoreColorVar(application.lynqScore)}) 14%, transparent)`,
            }}
          />
        )}
        <Link
          to={`/job/${application.jobId}/details`}
          className="application-card-actions"
          aria-label={`${t.seeDetails} — ${application.jobTitle}`}
        >
          <span className="application-card-details">{t.seeDetails}</span>
          <span className="application-card-chevron" aria-hidden="true">
            ›
          </span>
        </Link>
      </div>
    </article>
  )
}

export default ApplicationCard
