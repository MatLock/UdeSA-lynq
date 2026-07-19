import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import TitleOutlinedIcon from '@mui/icons-material/TitleOutlined'
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import BusinessCenterOutlinedIcon from '@mui/icons-material/BusinessCenterOutlined'
import ToggleOnOutlinedIcon from '@mui/icons-material/ToggleOnOutlined'
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined'
import LoadingOverlay from '../../components/LoadingOverlay/LoadingOverlay'
import SkillsField from '../../components/SkillsField/SkillsField'
import Spinner from '../../components/Spinner/Spinner'
import Toast from '../../components/Toast/Toast'
import useAuth from '../../hooks/useAuth'
import useApi from '../../hooks/useApi'
import jobService from '../../services/jobService'
import strings from '../../i18n'
import './EditJobPage.css'

// Edit form for a job post the signed-in user owns. Reuses the create-job form
// design language (and its CSS, imported by EditJobPage.css). The job is handed
// in via router state from the my-job-posts list so the form prefills instantly;
// on a direct hit / refresh it falls back to fetching the job's details. Unlike
// create-job it exposes the lifecycle status (OPEN/CLOSE), which the backend
// requires on every update. Company-only: candidates are bounced to the feed.
const EditJobPage = () => {
  const t = strings.pages.editJob
  const { user } = useAuth()
  // Refresh-aware fetcher so a long-open form still submits past token expiry.
  const { authFetch } = useApi()
  const navigate = useNavigate()
  const { jobId } = useParams()
  const location = useLocation()
  // The list passes the already-loaded job along, so we can render instantly.
  const passedJob = location.state?.job

  const [loading, setLoading] = useState(!passedJob)
  const [notFound, setNotFound] = useState(false)

  // Salary bounds become strings so the number inputs stay controlled and empty
  // bounds render blank rather than "null". When the job arrived via router
  // state we can seed the form up front; otherwise it's filled in by the fetch.
  const [title, setTitle] = useState(passedJob?.title ?? '')
  const [description, setDescription] = useState(passedJob?.description ?? '')
  const [workType, setWorkType] = useState(passedJob?.workType ?? '')
  const [status, setStatus] = useState(passedJob?.jobStatus ?? 'OPEN')
  const [salaryRangeDown, setSalaryRangeDown] = useState(
    passedJob?.salaryRangeDown != null ? String(passedJob.salaryRangeDown) : '',
  )
  const [salaryRangeTop, setSalaryRangeTop] = useState(
    passedJob?.salaryRangeTop != null ? String(passedJob.salaryRangeTop) : '',
  )
  const [skills, setSkills] = useState(passedJob?.skills ?? [])

  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [toast, setToast] = useState(null)

  useEffect(() => {
    // Seeded from router state already — nothing to fetch.
    if (passedJob) return

    // Direct hit / refresh: recover the job from the details endpoint.
    let cancelled = false
    const loadJob = async () => {
      setLoading(true)
      try {
        const job = await jobService.get_job_details(authFetch, jobId)
        if (cancelled) return
        setTitle(job.title ?? '')
        setDescription(job.description ?? '')
        setWorkType(job.workType ?? '')
        setStatus(job.jobStatus ?? 'OPEN')
        setSalaryRangeDown(job.salaryRangeDown != null ? String(job.salaryRangeDown) : '')
        setSalaryRangeTop(job.salaryRangeTop != null ? String(job.salaryRangeTop) : '')
        setSkills(job.skills ?? [])
      } catch {
        if (!cancelled) setNotFound(true)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    loadJob()

    return () => {
      cancelled = true
    }
    // passedJob is a stable navigation payload; fetch once per job id.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authFetch, jobId])

  // Company-only page: send everyone else back to the feed.
  if (user && user.userType !== 'COMPANY') {
    return <Navigate to="/home" replace />
  }

  const validate = () => {
    const next = {}
    if (!title.trim()) next.title = t.errors.titleRequired
    if (!description.trim()) next.description = t.errors.descriptionRequired
    if (!workType) next.workType = t.errors.workTypeRequired
    if (
      salaryRangeDown !== '' &&
      salaryRangeTop !== '' &&
      Number(salaryRangeTop) < Number(salaryRangeDown)
    ) {
      next.salaryRangeTop = t.errors.salaryRangeInvalid
    }
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setToast(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      await jobService.update_job(authFetch, jobId, {
        title: title.trim(),
        description: description.trim(),
        workType,
        status,
        salaryRangeDown,
        salaryRangeTop,
        skills,
      })
      // Land back on the management list where the change is reflected.
      navigate('/job/mine')
    } catch (error) {
      setToast({ type: 'error', message: error.reason ?? error.message ?? t.error })
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div className="login-bg create-job-page">
        <div className="edit-job-state">
          <Spinner label={t.loading} />
        </div>
      </div>
    )
  }

  if (notFound) {
    return (
      <div className="login-bg create-job-page">
        <main className="login-card create-job-card edit-job-notfound">
          <h1 className="login-title">{t.notFoundTitle}</h1>
          <p className="login-subtitle">{t.notFoundBody}</p>
          <button
            type="button"
            className="create-job-cancel"
            onClick={() => navigate('/job/mine')}
          >
            {t.backToPosts}
          </button>
        </main>
      </div>
    )
  }

  return (
    <div className="login-bg create-job-page">
      {submitting && <LoadingOverlay label={t.saving} />}
      <Toast
        message={toast?.message}
        type={toast?.type}
        onClose={() => setToast(null)}
      />
      <div className="login-dots login-dots-tl" />
      <div className="login-dots login-dots-br" />

      <main className="login-card create-job-card">
        <h1 className="login-title">{t.title}</h1>
        <p className="login-subtitle">{t.subtitle}</p>

        <form className="create-job-form" onSubmit={handleSubmit} noValidate>
          <div className="create-job-field">
            <label htmlFor="edit-job-title">{t.titleLabel}</label>
            <div className="create-job-control">
              <span className="create-job-field-icon tone-blue">
                <TitleOutlinedIcon sx={{ fontSize: 18 }} />
              </span>
              <input
                id="edit-job-title"
                placeholder={t.titlePlaceholder}
                value={title}
                aria-invalid={Boolean(errors.title)}
                onChange={(event) => setTitle(event.target.value)}
              />
            </div>
            {errors.title && (
              <p className="create-job-error" role="alert">{errors.title}</p>
            )}
          </div>

          <div className="create-job-field">
            <label htmlFor="edit-job-description">{t.descriptionLabel}</label>
            <div className="create-job-control create-job-control--textarea">
              <span className="create-job-field-icon tone-purple">
                <DescriptionOutlinedIcon sx={{ fontSize: 18 }} />
              </span>
              <textarea
                id="edit-job-description"
                className="create-job-textarea"
                rows={5}
                placeholder={t.descriptionPlaceholder}
                value={description}
                aria-invalid={Boolean(errors.description)}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>
            {errors.description && (
              <p className="create-job-error" role="alert">{errors.description}</p>
            )}
          </div>

          <div className="create-job-row">
            <div className="create-job-field">
              <label htmlFor="edit-job-worktype">{t.workTypeLabel}</label>
              <div className="create-job-control">
                <span className="create-job-field-icon tone-blue">
                  <BusinessCenterOutlinedIcon sx={{ fontSize: 18 }} />
                </span>
                <select
                  id="edit-job-worktype"
                  className="create-job-select"
                  value={workType}
                  aria-invalid={Boolean(errors.workType)}
                  onChange={(event) => setWorkType(event.target.value)}
                >
                  <option value="" disabled>
                    {t.workTypePlaceholder}
                  </option>
                  <option value="REMOTE">{t.workTypeRemote}</option>
                  <option value="IN_OFFICE">{t.workTypeInOffice}</option>
                </select>
              </div>
              {errors.workType && (
                <p className="create-job-error" role="alert">{errors.workType}</p>
              )}
            </div>

            <div className="create-job-field">
              <label htmlFor="edit-job-status">{t.statusLabel}</label>
              <div className="create-job-control">
                <span className="create-job-field-icon tone-purple">
                  <ToggleOnOutlinedIcon sx={{ fontSize: 18 }} />
                </span>
                <select
                  id="edit-job-status"
                  className="create-job-select"
                  value={status}
                  onChange={(event) => setStatus(event.target.value)}
                >
                  <option value="OPEN">{t.statusOpen}</option>
                  <option value="CLOSE">{t.statusClose}</option>
                </select>
              </div>
            </div>
          </div>

          <div className="create-job-row">
            <div className="create-job-field">
              <label htmlFor="edit-job-salary-down">{t.salaryDownLabel}</label>
              <div className="create-job-control">
                <span className="create-job-field-icon tone-purple">
                  <PaymentsOutlinedIcon sx={{ fontSize: 18 }} />
                </span>
                <input
                  id="edit-job-salary-down"
                  type="number"
                  min="0"
                  placeholder={t.salaryDownPlaceholder}
                  value={salaryRangeDown}
                  onChange={(event) => setSalaryRangeDown(event.target.value)}
                />
              </div>
            </div>

            <div className="create-job-field">
              <label htmlFor="edit-job-salary-top">{t.salaryTopLabel}</label>
              <div className="create-job-control">
                <span className="create-job-field-icon tone-purple">
                  <PaymentsOutlinedIcon sx={{ fontSize: 18 }} />
                </span>
                <input
                  id="edit-job-salary-top"
                  type="number"
                  min="0"
                  placeholder={t.salaryTopPlaceholder}
                  value={salaryRangeTop}
                  aria-invalid={Boolean(errors.salaryRangeTop)}
                  onChange={(event) => setSalaryRangeTop(event.target.value)}
                />
              </div>
              {errors.salaryRangeTop && (
                <p className="create-job-error" role="alert">{errors.salaryRangeTop}</p>
              )}
            </div>
          </div>

          <SkillsField
            title={title}
            description={description}
            workType={workType}
            skills={skills}
            onChange={setSkills}
            authFetch={authFetch}
            onError={(message) => setToast({ type: 'error', message })}
          />

          <div className="create-job-actions">
            <button
              type="button"
              className="create-job-cancel"
              onClick={() => navigate('/job/mine')}
              disabled={submitting}
            >
              {t.cancel}
            </button>
            <button type="submit" className="create-job-submit" disabled={submitting}>
              {t.submit}
            </button>
          </div>
        </form>
      </main>
    </div>
  )
}

export default EditJobPage
