import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import strings, { activeLocale } from '../../i18n'
import useApi from '../../hooks/useApi'
import companyService from '../../services/companyService'
import CompanyIcon from '../../components/CompanyIcon/CompanyIcon.jsx'
import Spinner from '../../components/Spinner/Spinner.jsx'
import './CompanyDetailPage.css'

// Public company profile, reached from a job's "View company" link
// (/company/:companyId). Fetches GET /company/{companyId}; the payload carries
// the company's profile plus the jobs it has posted. Mirrors the user detail
// page: a compact identity header, the About text full-width beneath it, and a
// carousel of squared job cards.

// The squared carousel cards only preview a job, so title and description are
// hard-truncated to a fixed length — the full text lives on the job detail page
// a card-click away.
const MAX_JOB_TITLE_LENGTH = 48
const MAX_JOB_DESCRIPTION_LENGTH = 90

// Clip to a preview length, appending an ellipsis only when text was actually
// dropped so short values stay untouched.
const truncate = (text, max) =>
  text && text.length > max ? `${text.slice(0, max).trimEnd()}…` : text ?? ''

// Format the LocalDate (YYYY-MM-DD) join date as a localized month + year.
const formatJoinDate = (iso) => {
  if (!iso) return null
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return null
  return new Intl.DateTimeFormat(activeLocale, {
    year: 'numeric',
    month: 'long',
  }).format(date)
}

const CompanyDetailPage = () => {
  const t = strings.companyProfile
  const { companyId } = useParams()
  const navigate = useNavigate()
  const { authFetch } = useApi()

  const [company, setCompany] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  // Posted-jobs carousel: the scroll track and the index of the card currently
  // in view, which drives the dotted indicator below the track.
  const jobsTrackRef = useRef(null)
  const [activeJob, setActiveJob] = useState(0)

  useEffect(() => {
    if (!companyId) return
    let cancelled = false
    const load = async () => {
      setLoading(true)
      setError(false)
      try {
        const data = await companyService.get_company_detail(authFetch, companyId)
        if (!cancelled) setCompany(data)
      } catch {
        if (!cancelled) setError(true)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [authFetch, companyId])

  const goBack = () => navigate(-1)

  // Scroll the carousel so the chosen card aligns to the start (scroll-snap
  // settles the exact position). offsetLeft is measured against the track, which
  // is position:relative.
  const scrollToJob = (index) => {
    const track = jobsTrackRef.current
    const card = track?.children[index]
    if (track && card) {
      track.scrollTo({ left: card.offsetLeft, behavior: 'smooth' })
    }
  }

  // Mark the card nearest the track's left edge as active, so the dots follow
  // both dot clicks and manual/trackpad scrolling.
  const handleJobsScroll = () => {
    const track = jobsTrackRef.current
    if (!track) return
    const { scrollLeft } = track
    let closest = 0
    let min = Infinity
    Array.from(track.children).forEach((card, index) => {
      const distance = Math.abs(card.offsetLeft - scrollLeft)
      if (distance < min) {
        min = distance
        closest = index
      }
    })
    setActiveJob(closest)
  }

  if (loading) {
    return (
      <div className="company-detail-page">
        <div className="company-detail-container">
          <button type="button" className="company-detail-back" onClick={goBack}>
            <span aria-hidden="true">←</span> {t.back}
          </button>
          <div className="company-detail-empty">
            <Spinner label={t.loading} />
          </div>
        </div>
      </div>
    )
  }

  if (error || !company) {
    return (
      <div className="company-detail-page">
        <div className="company-detail-container">
          <button type="button" className="company-detail-back" onClick={goBack}>
            <span aria-hidden="true">←</span> {t.back}
          </button>
          <div className="company-detail-empty">
            <h1 className="company-detail-empty-title">{t.notFoundTitle}</h1>
            <p className="company-detail-empty-body">{t.notFoundBody}</p>
          </div>
        </div>
      </div>
    )
  }

  const jobs = company.jobs ?? []
  const joinDate = formatJoinDate(company.createdOn)

  return (
    <div className="company-detail-page">
      <div className="company-detail-container">
        <button type="button" className="company-detail-back" onClick={goBack}>
          <span aria-hidden="true">←</span> {t.back}
        </button>

        {/* Hero: compact identity (logo, name, size, join date) at the top-left,
            with the About text spanning the full card width below it. */}
        <section className="company-detail-hero">
          <div className="company-detail-hero-head">
            <span className="company-detail-logo">
              {company.profileImageUrl ? (
                <img src={company.profileImageUrl} alt={t.logoAlt} />
              ) : (
                <CompanyIcon />
              )}
            </span>

            <div className="company-detail-hero-main">
              <h1 className="company-detail-name">{company.name}</h1>
              <div className="company-detail-meta">
                {company.size != null && (
                  <span className="company-detail-size">
                    {t.size.replace('{count}', company.size)}
                  </span>
                )}
                {company.size != null && joinDate && (
                  <span className="company-detail-sep" aria-hidden="true">
                    •
                  </span>
                )}
                {joinDate && (
                  <span>{t.memberSince.replace('{date}', joinDate)}</span>
                )}
              </div>
            </div>
          </div>

          <div className="company-detail-hero-about">
            <h2 className="company-detail-card-title">{t.aboutHeading}</h2>
            <p className="company-detail-about">{company.about || t.noAbout}</p>
          </div>
        </section>

        <div className="company-detail-grid">
          <section className="company-detail-card">
            <h2 className="company-detail-card-title">
              {t.jobsHeading}
              {jobs.length > 0 && (
                <span className="company-detail-jobs-count">
                  {t.jobsCount.replace('{count}', jobs.length)}
                </span>
              )}
            </h2>
            {jobs.length > 0 ? (
              <div className="company-detail-jobs-carousel">
                <div
                  className="company-detail-jobs-track"
                  ref={jobsTrackRef}
                  onScroll={handleJobsScroll}
                >
                  {jobs.map((job) => (
                    <Link
                      key={job.id}
                      to={`/job/${job.id}/details`}
                      className={`company-detail-job-card${
                        job.jobStatus === 'CLOSE' ? ' is-closed' : ''
                      }`}
                    >
                      {job.jobStatus === 'CLOSE' && (
                        <span className="company-detail-job-badge">
                          {t.closedBadge}
                        </span>
                      )}
                      <span className="company-detail-job-card-title">
                        {truncate(job.title, MAX_JOB_TITLE_LENGTH)}
                      </span>
                      {job.description && (
                        <span className="company-detail-job-card-desc">
                          {truncate(job.description, MAX_JOB_DESCRIPTION_LENGTH)}
                        </span>
                      )}
                      <span className="company-detail-job-card-cta">
                        {t.viewJob}
                        <span aria-hidden="true"> ›</span>
                      </span>
                    </Link>
                  ))}
                </div>

                {/* Dotted scroll indicator; each dot maps to a card and the
                    active one tracks the card in view. */}
                {jobs.length > 1 && (
                  <div className="company-detail-jobs-dots">
                    {jobs.map((job, index) => (
                      <button
                        key={job.id}
                        type="button"
                        className={`company-detail-jobs-dot${
                          index === activeJob ? ' is-active' : ''
                        }`}
                        aria-label={job.title}
                        aria-current={index === activeJob}
                        onClick={() => scrollToJob(index)}
                      />
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <p className="company-detail-muted">{t.noJobs}</p>
            )}
          </section>
        </div>
      </div>
    </div>
  )
}

export default CompanyDetailPage
