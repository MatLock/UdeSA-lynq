import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import strings from '../../i18n'
import useApi from '../../hooks/useApi'
import useAuth from '../../hooks/useAuth'
import jobService from '../../services/jobService'
import JobCard from '../../components/JobCard/JobCard.jsx'
import Pagination from '../../components/Pagination/Pagination.jsx'
import Spinner from '../../components/Spinner/Spinner.jsx'
import './HomePage.css'

// The landing page: on mount it lists the first page of available jobs and lets
// the user narrow them with a single free-text search. `query` is the live input
// text; `filterValue` is the term actually applied to the backend (only updated
// when Search is submitted, so typing doesn't refetch on every keystroke).
const PAGE_SIZE = 20

const HomePage = () => {
  const t = strings.home
  // authFetch transparently refreshes an expired access token and retries once
  // before surfacing an error, so the feed survives token expiry.
  const { authFetch } = useApi()
  const { user } = useAuth()
  const navigate = useNavigate()
  // Only company owners can publish jobs, so the action is theirs alone.
  const isCompany = user?.userType === 'COMPANY'

  const [query, setQuery] = useState('')
  const [filterValue, setFilterValue] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  // Refetch whenever the applied search or the page changes. A cancel flag drops
  // the result of a superseded request so a slow earlier fetch can't overwrite a
  // newer one.
  useEffect(() => {
    let cancelled = false
    const loadJobs = async () => {
      setLoading(true)
      setError(false)
      try {
        const result = await jobService.get_jobs(authFetch, {
          page,
          size: PAGE_SIZE,
          filterValue: filterValue || undefined,
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
  }, [authFetch, page, filterValue])

  // Applying a new search always restarts at the first page.
  const handleSearch = useCallback(
    (event) => {
      event.preventDefault()
      setPage(0)
      setFilterValue(query.trim())
    },
    [query],
  )

  const jobs = data?.content ?? []

  return (
    <div className="home-page">
      <header className="home-hero">
        <h1 className="home-banner">LYNQ</h1>
        <form className="home-search" role="search" onSubmit={handleSearch}>
          <div className="home-search-field">
            <svg
              className="home-search-icon"
              viewBox="0 0 24 24"
              fill="none"
              aria-hidden="true"
            >
              <circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth="2" />
              <path
                d="m20 20-3.5-3.5"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
              />
            </svg>
            <input
              type="search"
              className="home-search-input"
              placeholder={t.searchPlaceholder}
              aria-label={t.searchPlaceholder}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </div>
          <button type="submit" className="home-search-button">
            {t.searchButton}
          </button>
          {isCompany && (
            <button
              type="button"
              className="home-create-button"
              onClick={() => navigate('/create-job')}
            >
              <span className="home-create-plus" aria-hidden="true">
                +
              </span>
              {t.createJob}
            </button>
          )}
        </form>
      </header>

      <main className="home-results">
        {loading ? (
          <div className="home-state">
            <Spinner label={t.loading} />
          </div>
        ) : error ? (
          <p className="home-state home-error">{t.error}</p>
        ) : jobs.length === 0 ? (
          <p className="home-state">{t.empty}</p>
        ) : (
          <div className="home-jobs">
            {jobs.map((job) => (
              <JobCard key={job.jobId} job={job} />
            ))}
          </div>
        )}
      </main>

      {!loading && !error && (
        <footer className="home-pagination">
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

export default HomePage
