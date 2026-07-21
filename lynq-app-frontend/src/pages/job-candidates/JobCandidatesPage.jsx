import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { Chip } from '@mui/material'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'
import strings from '../../i18n'
import useApi from '../../hooks/useApi'
import useAuth from '../../hooks/useAuth'
import jobService from '../../services/jobService'
import UserIcon from '../../components/UserIcon/UserIcon.jsx'
import Pagination from '../../components/Pagination/Pagination.jsx'
import Spinner from '../../components/Spinner/Spinner.jsx'
import LoadingOverlay from '../../components/LoadingOverlay/LoadingOverlay.jsx'
import Toast from '../../components/Toast/Toast.jsx'
import CandidateEvaluationModal from '../../components/CandidateEvaluationModal/CandidateEvaluationModal.jsx'
import formatRelativeDate from '../../utils/formatRelativeDate'
import './JobCandidatesPage.css'

// The candidates that applied to one of the owner's job posts, reached from the
// "see candidates" link on the my-job-posts cards (/job/:jobId/candidates). Pages
// through GET /job/{jobId}/candidates and renders each applicant as a row with
// their profile, application date and LYNQ match score. Company-only, mirroring
// the my-job-posts list: candidates are bounced to the feed.
const PAGE_SIZE = 10

// Map a 0–100 LYNQ score to its band color token (see index.css --score-*),
// matching the JobCard scoring bands.
const scoreColorVar = (score) => {
  if (score <= 20) return '--score-red'
  if (score <= 40) return '--score-orange'
  if (score <= 60) return '--score-yellow'
  if (score <= 80) return '--score-light-green'
  return '--score-green'
}

const JobCandidatesPage = () => {
  const t = strings.pages.jobCandidates
  const { jobId } = useParams()
  // The my-job-posts link hands the already-loaded job along via router state so
  // the header can show its title without a redundant fetch.
  const { state } = useLocation()
  const jobTitle = state?.job?.title ?? null
  const { authFetch } = useApi()
  const { user } = useAuth()
  const navigate = useNavigate()

  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  // AI evaluation state: the userId currently being evaluated (drives the
  // per-row button + blocking overlay), the last result to show inline, and a
  // toast for failures — mirroring the skill-enhance flow (overlay + Toast).
  const [evaluatingId, setEvaluatingId] = useState(null)
  const [explanation, setExplanation] = useState(null)
  const [toast, setToast] = useState(null)

  // Refetch on page change. A cancel flag drops the result of a superseded
  // request so a slow earlier fetch can't overwrite a newer one.
  useEffect(() => {
    if (!jobId) return
    let cancelled = false
    const loadCandidates = async () => {
      setLoading(true)
      setError(false)
      // A new page invalidates any open evaluation panel.
      setExplanation(null)
      try {
        const result = await jobService.get_job_candidates(authFetch, jobId, {
          page,
          pageSize: PAGE_SIZE,
        })
        if (!cancelled) setData(result)
      } catch {
        if (!cancelled) setError(true)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    loadCandidates()

    return () => {
      cancelled = true
    }
  }, [authFetch, jobId, page])

  // Request the AI hiring evaluation for one applicant. The backend reads the job
  // and candidate from the DB, so we only pass the ids (candidate.userId is the
  // applicant's user id the endpoint looks up). The result opens in a modal.
  const handleEvaluate = async (candidate) => {
    if (evaluatingId || !candidate.userId) return
    setEvaluatingId(candidate.userId)
    try {
      const result = await jobService.get_candidate_explanation(
        authFetch,
        jobId,
        candidate.userId,
      )
      setExplanation({ candidate, result })
    } catch (err) {
      setToast({ type: 'error', message: err.reason ?? err.message ?? t.aiError })
    } finally {
      setEvaluatingId(null)
    }
  }

  // Company-only page: send everyone else back to the feed.
  if (user && user.userType !== 'COMPANY') {
    return <Navigate to="/home" replace />
  }

  const candidates = data?.content ?? []

  return (
    <div className="job-candidates-page">
      <header className="job-candidates-hero">
        <button
          type="button"
          className="job-candidates-back"
          onClick={() => navigate(-1)}
        >
          <span aria-hidden="true">←</span> {t.back}
        </button>
        <h1 className="job-candidates-title">{t.title}</h1>
        <p className="job-candidates-subtitle">
          {jobTitle
            ? t.subtitleFor.replace('{job}', jobTitle)
            : t.subtitle}
        </p>
        {!loading && !error && candidates.length > 0 && (
          <span className="job-candidates-count">
            {t.count.replace('{count}', data?.totalElements ?? candidates.length)}
          </span>
        )}
      </header>

      <main className="job-candidates-results">
        {loading ? (
          <div className="job-candidates-state">
            <Spinner label={t.loading} />
          </div>
        ) : error ? (
          <p className="job-candidates-state job-candidates-error">{t.error}</p>
        ) : candidates.length === 0 ? (
          <div className="job-candidates-state job-candidates-empty">
            <p>{t.empty}</p>
          </div>
        ) : (
          <div className="job-candidates-list">
            {candidates.map((candidate) => {
              const appliedOn = formatRelativeDate(candidate.userAppliedOn)
              const isEvaluating = evaluatingId === candidate.userId
              return (
                <article className="candidate-card" key={candidate.id}>
                  <span className="candidate-avatar">
                    {candidate.userProfileImage ? (
                      <img src={candidate.userProfileImage} alt={candidate.userFullName ?? ''} />
                    ) : (
                      <UserIcon />
                    )}
                  </span>

                  <div className="candidate-main">
                    {/* Name and LYNQ score share the top line: the score chip
                        sits right next to the applicant's name. */}
                    <div className="candidate-name-row">
                      {candidate.userId ? (
                        <Link
                          to={`/user/${candidate.userId}`}
                          state={{ lynqScore: candidate.lynqScore }}
                          className="candidate-name"
                        >
                          {candidate.userFullName ?? t.unknownCandidate}
                        </Link>
                      ) : (
                        <span className="candidate-name">
                          {candidate.userFullName ?? t.unknownCandidate}
                        </span>
                      )}
                      {candidate.lynqScore != null && (
                        <Chip
                          label={`${t.lynqScore}: ${candidate.lynqScore}`}
                          size="small"
                          variant="outlined"
                          className="candidate-score-chip"
                          sx={{
                            height: 22,
                            fontSize: 10,
                            fontWeight: 700,
                            '& .MuiChip-label': { px: 1.2 },
                            color: `var(${scoreColorVar(candidate.lynqScore)})`,
                            borderColor: `var(${scoreColorVar(candidate.lynqScore)})`,
                            backgroundColor: `color-mix(in srgb, var(${scoreColorVar(candidate.lynqScore)}) 14%, transparent)`,
                          }}
                        />
                      )}
                    </div>
                    <p className="candidate-position">
                      {candidate.userCurrentPosition || t.noPosition}
                    </p>
                    {appliedOn && (
                      <span className="candidate-applied">
                        {t.applied} {appliedOn}
                      </span>
                    )}
                  </div>

                  {/* Trailing actions: AI evaluation + review on their profile. */}
                  {candidate.userId && (
                    <div className="candidate-actions">
                      <button
                        type="button"
                        className="candidate-ai-button"
                        onClick={() => handleEvaluate(candidate)}
                        disabled={Boolean(evaluatingId)}
                        aria-haspopup="dialog"
                        aria-label={`${t.aiEvaluation} — ${candidate.userFullName ?? t.unknownCandidate}`}
                      >
                        <AutoAwesomeOutlinedIcon sx={{ fontSize: 16 }} />
                        {isEvaluating ? t.aiEvaluating : t.aiEvaluation}
                      </button>
                      <Link
                        to={`/user/${candidate.userId}`}
                        state={{ lynqScore: candidate.lynqScore }}
                        className="candidate-review"
                        aria-label={`${t.reviewApplication} — ${candidate.userFullName ?? t.unknownCandidate}`}
                      >
                        {t.reviewApplication}
                      </Link>
                    </div>
                  )}
                </article>
              )
            })}
          </div>
        )}
      </main>

      {!loading && !error && candidates.length > 0 && (
        <footer className="job-candidates-pagination">
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 0}
            hasPrevious={data?.hasPrevious ?? false}
            hasNext={data?.hasNext ?? false}
            onPageChange={setPage}
          />
        </footer>
      )}

      {evaluatingId && <LoadingOverlay label={t.aiEvaluating} />}

      {explanation && (
        <CandidateEvaluationModal
          candidateName={explanation.candidate?.userFullName ?? t.unknownCandidate}
          result={explanation.result}
          onClose={() => setExplanation(null)}
        />
      )}

      <Toast
        message={toast?.message}
        type={toast?.type}
        onClose={() => setToast(null)}
      />
    </div>
  )
}

export default JobCandidatesPage
