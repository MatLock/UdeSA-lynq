import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import strings from '../../i18n'
import useApi from '../../hooks/useApi'
import useAuth from '../../hooks/useAuth'
import jobService from '../../services/jobService'
import JobCard from '../../components/JobCard/JobCard.jsx'
import Pagination from '../../components/Pagination/Pagination.jsx'
import Spinner from '../../components/Spinner/Spinner.jsx'
import './MyJobPostsPage.css'

// The owner's job-post management list. On mount it pages through GET /job/mine
// — every job the signed-in user created, OPEN and CLOSE alike — and renders
// each as a JobCard with its lifecycle badge and an Edit action that hands the
// already-loaded job to the edit page via router state. Company-only, mirroring
// create-job: candidates are bounced to the feed.
const PAGE_SIZE = 20

const MyJobPostsPage = () => {
  const t = strings.pages.jobPosts
  // Refresh-aware fetcher so the list survives access-token expiry.
  const { authFetch } = useApi()
  const { user } = useAuth()
  const navigate = useNavigate()

  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  // Refetch on page change. A cancel flag drops the result of a superseded
  // request so a slow earlier fetch can't overwrite a newer one.
  useEffect(() => {
    let cancelled = false
    const loadJobs = async () => {
      setLoading(true)
      setError(false)
      try {
        const result = await jobService.get_my_jobs(authFetch, {
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
    loadJobs()

    return () => {
      cancelled = true
    }
  }, [authFetch, page])

  // Company-only page: send everyone else back to the feed.
  if (user && user.userType !== 'COMPANY') {
    return <Navigate to="/home" replace />
  }

  const jobs = data?.content ?? []

  return (
    <div className="my-jobs-page">
      <header className="my-jobs-hero">
        <h1 className="my-jobs-title">{t.title}</h1>
        <p className="my-jobs-subtitle">{t.subtitle}</p>
        {!loading && !error && jobs.length > 0 && (
          <span className="my-jobs-count">
            {t.count.replace('{count}', data?.totalElements ?? jobs.length)}
          </span>
        )}
      </header>

      <main className="my-jobs-results">
        {loading ? (
          <div className="my-jobs-state">
            <Spinner label={t.loading} />
          </div>
        ) : error ? (
          <p className="my-jobs-state my-jobs-error">{t.error}</p>
        ) : jobs.length === 0 ? (
          <div className="my-jobs-state my-jobs-empty">
            <p>{t.empty}</p>
            <button
              type="button"
              className="my-jobs-empty-cta"
              onClick={() => navigate('/create-job')}
            >
              <span aria-hidden="true">+</span>
              {t.emptyCreate}
            </button>
          </div>
        ) : (
          <div className="my-jobs-list">
            {jobs.map((job) => (
              <JobCard
                key={job.jobId}
                job={job}
                showScore={false}
                showStatus
                showCandidates
                actions={
                  <Link
                    to={`/job/${job.jobId}/edit`}
                    state={{ job }}
                    className="job-card-edit"
                    aria-label={`${strings.jobCard.edit} — ${job.title}`}
                  >
                    <EditOutlinedIcon sx={{ fontSize: 15 }} />
                    {strings.jobCard.edit}
                  </Link>
                }
              />
            ))}
          </div>
        )}
      </main>

      {!loading && !error && jobs.length > 0 && (
        <footer className="my-jobs-pagination">
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 0}
            hasPrevious={data?.hasPrevious ?? false}
            hasNext={data?.hasNext ?? false}
            onPageChange={setPage}
          />
        </footer>
      )}
    </div>
  )
}

export default MyJobPostsPage
