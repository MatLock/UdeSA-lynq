import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { Chip } from '@mui/material'
import strings from '../../i18n'
import { activeLocale } from '../../i18n'
import useApi from '../../hooks/useApi'
import useAuth from '../../hooks/useAuth'
import jobService from '../../services/jobService'
import CompanyIcon from '../../components/CompanyIcon/CompanyIcon.jsx'
import UserIcon from '../../components/UserIcon/UserIcon.jsx'
import Spinner from '../../components/Spinner/Spinner.jsx'
import formatRelativeDate from '../../utils/formatRelativeDate'
import './JobDetailPage.css'


const SOURCE_LABELS = {
  LINKEDIN: 'LinkedIn',
  COMPUTRABAJO: 'Computrabajo',
  BUMERAN: 'Bumeran',
}
const prettySource = (source) => SOURCE_LABELS[source] ?? source

// The work-type chip is filled with the matching brand color; an unknown type
// falls back to the brand blend.
const workTypeBg = (workType) =>
  workType === 'REMOTE'
    ? 'var(--brand-blue)'
    : workType === 'IN_OFFICE'
      ? 'var(--brand-purple)'
      : 'var(--brand-gradient)'

// Map a 0–100 LYNQ score to its band color token (see index.css --score-*).
const scoreColorVar = (score) => {
  if (score <= 20) return '--score-red'
  if (score <= 40) return '--score-orange'
  if (score <= 60) return '--score-yellow'
  if (score <= 80) return '--score-light-green'
  return '--score-green'
}

// Format a salary range from its (nullable) bounds. Numbers are grouped with the
// active locale's separators; when neither bound is present the caller shows the
// "not disclosed" copy instead.
const formatSalary = (down, top) => {
  const nf = new Intl.NumberFormat(activeLocale)
  const hasDown = down != null
  const hasTop = top != null
  if (hasDown && hasTop) return `${nf.format(down)} – ${nf.format(top)}`
  if (hasDown) return nf.format(down)
  if (hasTop) return nf.format(top)
  return null
}

const JobDetailPage = () => {
  const t = strings.jobDetail
  const location = useLocation()
  const { jobId } = useParams()
  const { authFetch } = useApi()
  const { user } = useAuth()
  // The JobCard click hands the job in via router state for an instant first
  // paint; the authoritative data (including the counters below) is then loaded
  // from GET /job/{jobId}/details, which also lets direct navigation / reload
  // work without any router state.
  const initialJob = location.state?.job ?? null
  const [job, setJob] = useState(initialJob)

  // Mirror the feed's rule (HomePage/JobCard): candidate-only concerns (the LYNQ
  // score, the Apply action) are shown to everyone except COMPANY users. We
  // derive from isCompany rather than a positive `userType === 'CANDIDATE'` check
  // so behavior matches the feed even when the profile hasn't populated userType
  // (e.g. the login profile lookup failed) — otherwise the score would show in
  // the feed but silently vanish here.
  const isCompany = user?.userType === 'COMPANY'
  const [applyState, setApplyState] = useState('idle') // idle|applying|applied|already|error
  // Owner-only close/re-open action. The button shown (and which endpoint it
  // hits) is derived from the job's live status; this only tracks the in-flight
  // request so the button can disable and surface an error.
  const [ownerAction, setOwnerAction] = useState('idle') // idle|working|error
  // The two counters returned by the details endpoint, seeded from the router
  // placeholder (if any) so they render immediately, then refreshed from the
  // fetch below.
  const [seenCount, setSeenCount] = useState(initialJob?.totalSeen ?? null)
  const [appliedCount, setAppliedCount] = useState(
    initialJob?.totalCandidatesApplied ?? null,
  )
  // Only block the page with a spinner when there's no placeholder to show yet
  // (direct navigation / reload); otherwise we render the placeholder and update
  // it in place once the details load.
  const [loading, setLoading] = useState(!initialJob)
  // Tracks the job we've already counted a view for, so the increment fires once
  // per job even through React StrictMode's dev double-mount (which re-runs the
  // effect and would otherwise count the same view twice).
  const seenCountedForRef = useRef(null)

  // On mount: count this view (best-effort — the details endpoint is read-only
  // and does not increment), then load the authoritative details and counters.
  useEffect(() => {
    if (!jobId) return
    let cancelled = false
    const load = async () => {
      if (seenCountedForRef.current !== jobId) {
        seenCountedForRef.current = jobId
        try {
          await jobService.increase_seen(authFetch, jobId)
        } catch {
          // the seen counter is non-critical; never let it block the details load
        }
      }
      try {
        const details = await jobService.get_job_details(authFetch, jobId)
        if (!cancelled && details) {
          setJob(details)
          setSeenCount(details.totalSeen ?? null)
          setAppliedCount(details.totalCandidatesApplied ?? null)
        }
      } catch {
        // keep the router-state placeholder; the guard below covers no-data
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [authFetch, jobId])

  const handleApply = async () => {
    if (applyState === 'applying' || applyState === 'applied') return
    setApplyState('applying')
    try {
      await jobService.apply_to_job(authFetch, jobId)
      setApplyState('applied')
    } catch (error) {
      // The backend replies 400 when the user has already applied — surface that
      // as an informative state rather than a generic error.
      setApplyState(error?.status === 400 ? 'already' : 'error')
    }
  }

  // Close the post (stop seeking) or re-open it, then flip the local job status
  // so the button swaps to its counterpart without a refetch.
  const handleOwnerAction = async (close) => {
    if (ownerAction === 'working') return
    setOwnerAction('working')
    try {
      if (close) {
        await jobService.close_job(authFetch, jobId)
      } else {
        await jobService.refresh_job(authFetch, jobId)
      }
      setJob((prev) =>
        prev ? { ...prev, jobStatus: close ? 'CLOSE' : 'OPEN' } : prev,
      )
      setOwnerAction('idle')
    } catch {
      setOwnerAction('error')
    }
  }

  // Details still loading with nothing to show yet (direct navigation / reload):
  // render a spinner rather than flashing the "unavailable" state.
  if (loading && !job) {
    return (
      <div className="job-detail-page">
        <div className="job-detail-container">
          <Link to="/home" className="job-detail-back">
            <span aria-hidden="true">←</span> {t.back}
          </Link>
          <div className="job-detail-empty">
            <Spinner label={t.loading} />
          </div>
        </div>
      </div>
    )
  }

  // Nothing to rehydrate from and the fetch produced no job (missing / failed),
  // so guide the user back to the feed.
  if (!job) {
    return (
      <div className="job-detail-page">
        <div className="job-detail-container">
          <Link to="/home" className="job-detail-back">
            <span aria-hidden="true">←</span> {t.back}
          </Link>
          <div className="job-detail-empty">
            <h1 className="job-detail-empty-title">{t.notFoundTitle}</h1>
            <p className="job-detail-empty-body">{t.notFoundBody}</p>
          </div>
        </div>
      </div>
    )
  }

  const workTypeLabel = strings.jobCard.workType[job.workType] ?? job.workType
  const publishedAt = formatRelativeDate(job.createdOn)
  const salary = formatSalary(job.salaryRangeDown, job.salaryRangeTop)
  const hasScore = !isCompany && job.lynqScore != null
  const skills = job.skills ?? []
  const isExternal = job.jobPostSource && job.jobPostSource !== 'LYNQ'
  const company = job.company ?? null
  const companyLogo = isExternal ? null : company?.profileImageUrl
  const recruiter = job.postedBy ?? null
  // The post belongs to the logged-in user when they are the one who created it.
  // Only LYNQ posts carry a poster, so external jobs never match.
  const isOwner =
    user?.id != null && recruiter?.id != null && user.id === recruiter.id
  // The details endpoint returns jobs of any status, so an owned post can be
  // closed — in which case the owner gets a re-open action instead of close.
  const isClosed = job.jobStatus === 'CLOSE'

  return (
    <div className="job-detail-page">
      <div className="job-detail-container">
        <Link to="/home" className="job-detail-back">
          <span aria-hidden="true">←</span> {t.back}
        </Link>

        {/* Hero: logo, tags, title, company line, salary, and the apply action. */}
        <section className="job-detail-hero">
          <span className="job-detail-logo">
            {companyLogo ? (
              <img src={companyLogo} alt={company?.name ?? t.companyLogoAlt} />
            ) : (
              <CompanyIcon />
            )}
          </span>

          <div className="job-detail-hero-main">
            <div className="job-detail-tags">
              <Chip
                label={workTypeLabel}
                size="small"
                sx={{
                  height: 24,
                  fontSize: 11,
                  fontWeight: 700,
                  letterSpacing: '0.3px',
                  textTransform: 'uppercase',
                  color: '#fff',
                  background: workTypeBg(job.workType),
                  '& .MuiChip-label': { px: 1.2 },
                }}
              />
            </div>

            <h1 className="job-detail-title">{job.title}</h1>

            <div className="job-detail-hero-meta">
              <span className="job-detail-company-name">
                {company?.name ?? t.unknownCompany}
              </span>
              {publishedAt && (
                <>
                  <span className="job-detail-sep" aria-hidden="true">
                    •
                  </span>
                  <span>
                    {t.postedLabel} {publishedAt}
                  </span>
                </>
              )}
            </div>

            {salary && (
              <p className="job-detail-hero-salary">
                <span className="job-detail-hero-salary-label">
                  {t.salaryLabel}
                </span>
                {salary}
              </p>
            )}
          </div>

          {isOwner ? (
            <div className="job-detail-hero-side">
              {/* Owner action: close the post to stop seeking candidates, or
                  re-open it (green) once it has been closed. */}
              <div className="job-detail-hero-actions">
                {isClosed ? (
                  <button
                    type="button"
                    className="job-detail-reopen"
                    onClick={() => handleOwnerAction(false)}
                    disabled={ownerAction === 'working'}
                  >
                    {ownerAction === 'working' ? t.reopening : t.reopen}
                  </button>
                ) : (
                  <button
                    type="button"
                    className="job-detail-stop"
                    onClick={() => handleOwnerAction(true)}
                    disabled={ownerAction === 'working'}
                  >
                    {ownerAction === 'working' ? t.stopping : t.stopSeeking}
                  </button>
                )}
                {isClosed && ownerAction !== 'error' && (
                  <p className="job-detail-apply-status is-info">
                    {t.closedNotice}
                  </p>
                )}
                {ownerAction === 'error' && (
                  <p className="job-detail-apply-status is-error">
                    {isClosed ? t.reopenError : t.stopError}
                  </p>
                )}
              </div>
            </div>
          ) : !isCompany ? (
            <div className="job-detail-hero-side">
              {/* LYNQ score pinned to the hero's top-right corner. */}
              {hasScore && (
                <Chip
                  label={`${t.lynqScoreLabel}: ${job.lynqScore}`}
                  size="small"
                  variant="outlined"
                  sx={{
                    height: 24,
                    fontSize: 11,
                    fontWeight: 700,
                    color: `var(${scoreColorVar(job.lynqScore)})`,
                    borderColor: `var(${scoreColorVar(job.lynqScore)})`,
                    backgroundColor: `color-mix(in srgb, var(${scoreColorVar(job.lynqScore)}) 14%, transparent)`,
                    '& .MuiChip-label': { px: 1.2 },
                  }}
                />
              )}
              {/* Apply action, vertically centered on the hero's right edge. */}
              <div className="job-detail-hero-actions">
                <button
                  type="button"
                  className="job-detail-apply"
                  onClick={handleApply}
                  disabled={applyState === 'applying' || applyState === 'applied'}
                >
                  {applyState === 'applying' ? t.applying : t.apply}
                </button>
                {applyState === 'applied' && (
                  <p className="job-detail-apply-status is-success">
                    {t.applied}
                  </p>
                )}
                {applyState === 'already' && (
                  <p className="job-detail-apply-status is-info">
                    {t.alreadyApplied}
                  </p>
                )}
                {applyState === 'error' && (
                  <p className="job-detail-apply-status is-error">
                    {t.applyError}
                  </p>
                )}
              </div>
            </div>
          ) : null}
        </section>

        <div className="job-detail-grid">
          {/* Left column: the long-form content. */}
          <div className="job-detail-col job-detail-col--main">
            <section className="job-detail-card">
              <h2 className="job-detail-card-title">{t.descriptionHeading}</h2>
              <p className="job-detail-description">
                {job.description || t.noDescription}
              </p>
            </section>

            <section className="job-detail-card">
              <h2 className="job-detail-card-title">{t.skillsHeading}</h2>
              {skills.length > 0 ? (
                <div className="job-detail-skills">
                  {skills.map((skill) => (
                    <Chip
                      key={skill}
                      label={skill}
                      size="small"
                      variant="outlined"
                      sx={{
                        height: 26,
                        fontSize: 12,
                        fontWeight: 600,
                        color: 'var(--brand-purple-dark)',
                        borderColor: 'var(--accent-border)',
                        backgroundColor: 'var(--accent-bg)',
                        '& .MuiChip-label': { px: 1.2 },
                      }}
                    />
                  ))}
                </div>
              ) : (
                <p className="job-detail-muted">{t.noSkills}</p>
              )}
            </section>
          </div>

          {/* Right column: at-a-glance facts, company and recruiter. */}
          <aside className="job-detail-col job-detail-col--side">
            <section className="job-detail-card">
              <h2 className="job-detail-card-title">{t.overviewHeading}</h2>
              <dl className="job-detail-facts">
                <div className="job-detail-fact">
                  <dt>{t.workTypeLabel}</dt>
                  <dd>{workTypeLabel}</dd>
                </div>
                <div className="job-detail-fact">
                  <dt>{t.salaryLabel}</dt>
                  <dd>{salary ?? t.salaryNotDisclosed}</dd>
                </div>
                {publishedAt && (
                  <div className="job-detail-fact">
                    <dt>{t.postedLabel}</dt>
                    <dd>{publishedAt}</dd>
                  </div>
                )}
                {isExternal && (
                  <div className="job-detail-fact">
                    <dt>{t.sourceLabel}</dt>
                    <dd>{prettySource(job.jobPostSource)}</dd>
                  </div>
                )}
                {seenCount != null && (
                  <div className="job-detail-fact">
                    <dt>{t.seenLabel}</dt>
                    <dd>{seenCount}</dd>
                  </div>
                )}
                {appliedCount != null && (
                  <div className="job-detail-fact">
                    <dt>{t.candidatesLabel}</dt>
                    <dd>{appliedCount}</dd>
                  </div>
                )}
                {hasScore && (
                  <div className="job-detail-fact">
                    <dt>{t.lynqScoreLabel}</dt>
                    <dd
                      style={{ color: `var(${scoreColorVar(job.lynqScore)})` }}
                    >
                      {job.lynqScore}
                    </dd>
                  </div>
                )}
              </dl>
            </section>

            <section className="job-detail-card">
              <h2 className="job-detail-card-title">{t.companyHeading}</h2>
              <div className="job-detail-entity">
                <span className="job-detail-entity-logo">
                  {companyLogo ? (
                    <img
                      src={companyLogo}
                      alt={company?.name ?? t.companyLogoAlt}
                    />
                  ) : (
                    <CompanyIcon />
                  )}
                </span>
                <div className="job-detail-entity-text">
                  <span className="job-detail-entity-name">
                    {company?.name ?? t.unknownCompany}
                  </span>
                  {company?.size != null && (
                    <span className="job-detail-entity-sub">
                      {t.companySize.replace('{count}', company.size)}
                    </span>
                  )}
                </div>
              </div>
              <p className="job-detail-about">
                {company?.about || t.noCompanyAbout}
              </p>
              {/* Links to the company's detail page at /company/{companyId}.
                  Falls back to nothing when the post carries no identified
                  company (e.g. external postings). */}
              {company?.id && (
                <Link
                  to={`/company/${company.id}`}
                  className="job-detail-external-link"
                >
                  {t.viewCompany}
                  <span aria-hidden="true"> ›</span>
                </Link>
              )}
            </section>

            {isExternal ? (
              <section className="job-detail-card">
                <h2 className="job-detail-card-title">{t.externalHeading}</h2>
                <p className="job-detail-about">
                  {t.externalBody.replace(
                    '{source}',
                    prettySource(job.jobPostSource),
                  )}
                </p>
                {job.jobUrl && (
                  <a
                    className="job-detail-external-link"
                    href={job.jobUrl}
                    target="_blank"
                    rel="noreferrer noopener"
                  >
                    {t.viewOriginal}
                    <span aria-hidden="true"> ↗</span>
                  </a>
                )}
              </section>
            ) : (
              <section className="job-detail-card">
                <h2 className="job-detail-card-title">{t.recruiterHeading}</h2>
                {recruiter ? (
                  <>
                    <div className="job-detail-entity">
                      <span className="job-detail-entity-avatar">
                        {recruiter.profileImageUrl ? (
                          <img
                            src={recruiter.profileImageUrl}
                            alt={recruiter.fullName ?? t.recruiterAvatarAlt}
                          />
                        ) : (
                          <UserIcon />
                        )}
                      </span>
                      <div className="job-detail-entity-text">
                        <span className="job-detail-entity-name">
                          {recruiter.fullName ?? t.unknownRecruiter}
                        </span>
                        {recruiter.currentPosition && (
                          <span className="job-detail-entity-sub">
                            {recruiter.currentPosition}
                          </span>
                        )}
                      </div>
                    </div>
                    {recruiter.id && (
                      <Link
                        to={`/user/${recruiter.id}`}
                        className="job-detail-external-link"
                      >
                        {t.viewProfile}
                        <span aria-hidden="true"> ›</span>
                      </Link>
                    )}
                  </>
                ) : (
                  <p className="job-detail-muted">{t.recruiterFallback}</p>
                )}
              </section>
            )}
          </aside>
        </div>
      </div>
    </div>
  )
}

export default JobDetailPage
