import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import GitHubIcon from '@mui/icons-material/GitHub'
import LinkedInIcon from '@mui/icons-material/LinkedIn'
import strings from '../../i18n'
import useApi from '../../hooks/useApi'
import userService from '../../services/userService'
import UserIcon from '../../components/UserIcon/UserIcon.jsx'
import CompanyIcon from '../../components/CompanyIcon/CompanyIcon.jsx'
import Spinner from '../../components/Spinner/Spinner.jsx'
import './UserProfilePage.css'

// Public profile for another user, reached from a job's poster/recruiter link
// (/user/:userId). Fetches GET /user/{userId}; for COMPANY users the payload
// also carries their company and the jobs they've posted, which get their own
// sections. Reuses the card language shared with the job detail page.

// The squared carousel cards only preview a job, so title and description are
// hard-truncated to a fixed length — the full text lives on the job detail page
// a card-click away. (CSS line-clamp alone is unreliable for long unbroken text.)
const MAX_JOB_TITLE_LENGTH = 48
const MAX_JOB_DESCRIPTION_LENGTH = 90

// Clip to a preview length, appending an ellipsis only when text was actually
// dropped so short values stay untouched.
const truncate = (text, max) =>
  text && text.length > max ? `${text.slice(0, max).trimEnd()}…` : text ?? ''

const UserProfilePage = () => {
  const t = strings.userProfile
  const { userId } = useParams()
  const navigate = useNavigate()
  const { authFetch } = useApi()

  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  // Posted-jobs carousel: the scroll track and the index of the card currently
  // in view, which drives the dotted indicator below the track.
  const jobsTrackRef = useRef(null)
  const [activeJob, setActiveJob] = useState(0)

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    const load = async () => {
      setLoading(true)
      setError(false)
      try {
        const data = await userService.get_user_profile(authFetch, userId)
        if (!cancelled) setProfile(data)
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
  }, [authFetch, userId])

  // Back to wherever the user came from (feed or a job detail), falling back to
  // the feed on direct navigation.
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
      <div className="user-profile-page">
        <div className="user-profile-container">
          <button type="button" className="user-profile-back" onClick={goBack}>
            <span aria-hidden="true">←</span> {t.back}
          </button>
          <div className="user-profile-empty">
            <Spinner label={t.loading} />
          </div>
        </div>
      </div>
    )
  }

  if (error || !profile) {
    return (
      <div className="user-profile-page">
        <div className="user-profile-container">
          <button type="button" className="user-profile-back" onClick={goBack}>
            <span aria-hidden="true">←</span> {t.back}
          </button>
          <div className="user-profile-empty">
            <h1 className="user-profile-empty-title">{t.notFoundTitle}</h1>
            <p className="user-profile-empty-body">{t.notFoundBody}</p>
          </div>
        </div>
      </div>
    )
  }

  const company = profile.company ?? null
  const jobs = profile.jobs ?? []
  const hasGithub = Boolean(profile.githubUrl)
  const hasLinkedin = Boolean(profile.linkedinUrl)
  const hasLinks = hasGithub || hasLinkedin
  // Only COMPANY users carry a company / posted jobs, so the two-column layout
  // (about + jobs) is reserved for them; candidates get a clean single column.
  const isCompanyProfile = Boolean(company) || jobs.length > 0

  return (
    <div className="user-profile-page">
      <div className="user-profile-container">
        <button type="button" className="user-profile-back" onClick={goBack}>
          <span aria-hidden="true">←</span> {t.back}
        </button>

        {/* Hero. Left half is the identity (avatar, name, position, company
            badge, social links). For company owners the right half is filled
            with the About text — the two halves carry equal weight. Candidates
            keep a single identity row with About in the grid below. */}
        <section
          className={`user-profile-hero${
            isCompanyProfile ? ' user-profile-hero--split' : ''
          }`}
        >
          <div className="user-profile-hero-left">
            <span className="user-profile-avatar">
              {profile.profileImageUrl ? (
                <img src={profile.profileImageUrl} alt={t.avatarAlt} />
              ) : (
                <UserIcon />
              )}
            </span>

            <div className="user-profile-hero-main">
              <h1 className="user-profile-name">{profile.fullName}</h1>
              <p className="user-profile-position">
                {profile.currentPosition || t.noPosition}
              </p>

              {company && (
                <div className="user-profile-company-badge">
                  <span className="user-profile-company-badge-logo">
                    {company.profileImageUrl ? (
                      <img src={company.profileImageUrl} alt={t.companyLogoAlt} />
                    ) : (
                      <CompanyIcon />
                    )}
                  </span>
                  {company.name}
                </div>
              )}

              {hasLinks && (
                <div className="user-profile-links">
                  {hasGithub && (
                    <a
                      className="user-profile-link"
                      href={profile.githubUrl}
                      target="_blank"
                      rel="noreferrer noopener"
                    >
                      <GitHubIcon sx={{ fontSize: 16 }} />
                      {t.github}
                    </a>
                  )}
                  {hasLinkedin && (
                    <a
                      className="user-profile-link"
                      href={profile.linkedinUrl}
                      target="_blank"
                      rel="noreferrer noopener"
                    >
                      <LinkedInIcon sx={{ fontSize: 16 }} />
                      {t.linkedin}
                    </a>
                  )}
                </div>
              )}
            </div>
          </div>

          {isCompanyProfile && (
            <div className="user-profile-hero-about">
              <h2 className="user-profile-card-title">{t.aboutHeading}</h2>
              <p className="user-profile-about">{profile.about || t.noAbout}</p>
            </div>
          )}
        </section>

        <div className="user-profile-grid user-profile-grid--single">
          {isCompanyProfile ? (
            <section className="user-profile-card">
              <h2 className="user-profile-card-title">
                {t.jobsHeading}
                {jobs.length > 0 && (
                  <span className="user-profile-jobs-count">
                    {t.jobsCount.replace('{count}', jobs.length)}
                  </span>
                )}
              </h2>
              {jobs.length > 0 ? (
                <div className="user-profile-jobs-carousel">
                  <div
                    className="user-profile-jobs-track"
                    ref={jobsTrackRef}
                    onScroll={handleJobsScroll}
                  >
                    {jobs.map((job) => (
                      <Link
                        key={job.id}
                        to={`/job/${job.id}/details`}
                        className={`user-profile-job-card${
                          job.jobStatus === 'CLOSE' ? ' is-closed' : ''
                        }`}
                      >
                        {job.jobStatus === 'CLOSE' && (
                          <span className="user-profile-job-badge">
                            {t.closedBadge}
                          </span>
                        )}
                        <span className="user-profile-job-card-title">
                          {truncate(job.title, MAX_JOB_TITLE_LENGTH)}
                        </span>
                        {job.description && (
                          <span className="user-profile-job-card-desc">
                            {truncate(job.description, MAX_JOB_DESCRIPTION_LENGTH)}
                          </span>
                        )}
                        <span className="user-profile-job-card-cta">
                          {t.viewJob}
                          <span aria-hidden="true"> ›</span>
                        </span>
                      </Link>
                    ))}
                  </div>

                  {/* Dotted scroll indicator; each dot maps to a card and the
                      active one tracks the card in view. */}
                  {jobs.length > 1 && (
                    <div className="user-profile-jobs-dots">
                      {jobs.map((job, index) => (
                        <button
                          key={job.id}
                          type="button"
                          className={`user-profile-jobs-dot${
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
                <p className="user-profile-muted">{t.noJobs}</p>
              )}
            </section>
          ) : (
            <section className="user-profile-card">
              <h2 className="user-profile-card-title">{t.aboutHeading}</h2>
              <p className="user-profile-about">{profile.about || t.noAbout}</p>
            </section>
          )}
        </div>
      </div>
    </div>
  )
}

export default UserProfilePage
