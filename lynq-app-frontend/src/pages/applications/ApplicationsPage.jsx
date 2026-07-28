import { useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import strings, { activeLocale } from '../../i18n'
import useApi from '../../hooks/useApi'
import useAuth from '../../hooks/useAuth'
import userService from '../../services/userService'
import ApplicationCard from '../../components/ApplicationCard/ApplicationCard.jsx'
import ScoreExplanationModal from '../../components/ScoreExplanationModal/ScoreExplanationModal.jsx'
import Pagination from '../../components/Pagination/Pagination.jsx'
import Spinner from '../../components/Spinner/Spinner.jsx'
import './ApplicationsPage.css'

// The candidate's "My Applications" list. On mount it pages through
// GET /user/application — every job the signed-in user applied to, most recent
// first — and renders each as an ApplicationCard with its LYNQ score and a
// shortcut to the job's detail page. Candidate-only: company users are bounced
// to the feed (mirroring the resume section).
const PAGE_SIZE = 10

const ApplicationsPage = () => {
  const t = strings.pages.applications
  // Refresh-aware fetcher so the list survives access-token expiry.
  const { authFetch } = useApi()
  const { user } = useAuth()
  const navigate = useNavigate()

  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  // Score-explanation modal state: the application currently being explained
  // (drives the modal + disables the buttons while a request is in flight), the
  // fetched suggestion result, and its own loading/error flags.
  const [explaining, setExplaining] = useState(null)
  const [explanationResult, setExplanationResult] = useState(null)
  const [explanationLoading, setExplanationLoading] = useState(false)
  const [explanationError, setExplanationError] = useState(false)

  // Refetch on page change. A cancel flag drops the result of a superseded
  // request so a slow earlier fetch can't overwrite a newer one.
  useEffect(() => {
    let cancelled = false
    const loadApplications = async () => {
      setLoading(true)
      setError(false)
      try {
        const result = await userService.get_user_applications(authFetch, {
          page,
          size: PAGE_SIZE,
        })
        if (!cancelled) setData(result)
      } catch {
        if (!cancelled) setError(true)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    loadApplications()

    return () => {
      cancelled = true
    }
  }, [authFetch, page])

  // Open the score-explanation modal for one application and fetch its AI
  // explanation + recommended courses. The modal opens immediately in a loading
  // state; the result (or error) fills in when the request settles.
  const handleExplain = async (application) => {
    if (explanationLoading) return
    setExplaining(application)
    setExplanationResult(null)
    setExplanationError(false)
    setExplanationLoading(true)
    try {
      const result = await userService.get_upskilling_suggestion(
        authFetch,
        application.jobId,
        activeLocale,
      )
      setExplanationResult(result)
    } catch {
      setExplanationError(true)
    } finally {
      setExplanationLoading(false)
    }
  }

  const handleCloseExplanation = () => {
    setExplaining(null)
    setExplanationResult(null)
    setExplanationError(false)
  }

  // Candidate-only page: send company users back to the feed.
  if (user?.userType === 'COMPANY') {
    return <Navigate to="/home" replace />
  }

  const applications = data?.content ?? []

  const renderResults = () => {
    if (loading) {
      return (
        <div className="applications-state">
          <Spinner label={t.loading} />
        </div>
      )
    }

    if (error) {
      return <p className="applications-state applications-error">{t.error}</p>
    }

    if (applications.length === 0) {
      return (
        <div className="applications-state applications-empty">
          <p>{t.empty}</p>
          <button
            type="button"
            className="applications-empty-cta"
            onClick={() => navigate('/home')}
          >
            {t.emptyCta}
          </button>
        </div>
      )
    }

    return (
      <div className="applications-list">
        {applications.map((application) => (
          <ApplicationCard
            key={application.id}
            application={application}
            onExplain={handleExplain}
            explainDisabled={explanationLoading}
          />
        ))}
      </div>
    )
  }

  return (
    <div className="applications-page">
      <header className="applications-hero">
        <h1 className="applications-title">{t.title}</h1>
        <p className="applications-subtitle">{t.subtitle}</p>
        {!loading && !error && applications.length > 0 && (
          <span className="applications-count">
            {t.count.replace('{count}', data?.totalElements ?? applications.length)}
          </span>
        )}
      </header>

      <main className="applications-results">{renderResults()}</main>

      {!loading && !error && applications.length > 0 && (
        <footer className="applications-pagination">
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 0}
            hasPrevious={data?.hasPrevious ?? false}
            hasNext={data?.hasNext ?? false}
            onPageChange={setPage}
          />
        </footer>
      )}

      {explaining && (
        <ScoreExplanationModal
          jobTitle={explaining.jobTitle}
          result={explanationResult}
          loading={explanationLoading}
          error={explanationError}
          onClose={handleCloseExplanation}
        />
      )}
    </div>
  )
}

export default ApplicationsPage
