import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import TitleOutlinedIcon from '@mui/icons-material/TitleOutlined'
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import BusinessCenterOutlinedIcon from '@mui/icons-material/BusinessCenterOutlined'
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined'
import LoadingOverlay from '../../components/LoadingOverlay/LoadingOverlay'
import SkillsField from '../../components/SkillsField/SkillsField'
import Toast from '../../components/Toast/Toast'
import useAuth from '../../hooks/useAuth'
import useApi from '../../hooks/useApi'
import jobService from '../../services/jobService'
import strings from '../../i18n'
import './CreateJobPage.css'

// Job-post creation form for company owners. The backend derives the company
// and posting user from the auth token (and rejects non-company users), so the
// form only collects the job's own fields. Reuses the auth card design language
// (matches login / register / profile). Candidates are bounced to /home since
// this page is company-only.
const CreateJobPage = () => {
  const t = strings.pages.createJob
  const { user } = useAuth()
  // Refresh-aware fetcher so a long-open form still submits past token expiry.
  const { authFetch } = useApi()
  const navigate = useNavigate()

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [workType, setWorkType] = useState('')
  const [salaryRangeDown, setSalaryRangeDown] = useState('')
  const [salaryRangeTop, setSalaryRangeTop] = useState('')
  // AI-generated then user-editable; the SkillsField component owns the editing
  // UI and generation, this page just holds the value that ships with the job.
  const [skills, setSkills] = useState([])

  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [toast, setToast] = useState(null)

  // Company-only page: send everyone else back to the feed.
  if (user && user.userType !== 'COMPANY') {
    return <Navigate to="/home" replace />
  }

  const validate = () => {
    const next = {}
    if (!title.trim()) next.title = t.errors.titleRequired
    if (!description.trim()) next.description = t.errors.descriptionRequired
    if (!workType) next.workType = t.errors.workTypeRequired
    // Only compare bounds when both are present.
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
      await jobService.create_job(authFetch, {
        title: title.trim(),
        description: description.trim(),
        workType,
        salaryRangeDown,
        salaryRangeTop,
        skills,
      })
      // Land back on the feed where the new post now appears.
      navigate('/home', { state: { jobCreated: true } })
    } catch (error) {
      setToast({ type: 'error', message: error.reason ?? error.message ?? t.error })
      setSubmitting(false)
    }
  }

  return (
    <div className="login-bg create-job-page">
      {submitting && <LoadingOverlay label={t.submitting} />}
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
            <label htmlFor="create-job-title">{t.titleLabel}</label>
            <div className="create-job-control">
              <span className="create-job-field-icon tone-blue">
                <TitleOutlinedIcon sx={{ fontSize: 18 }} />
              </span>
              <input
                id="create-job-title"
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
            <label htmlFor="create-job-description">{t.descriptionLabel}</label>
            <div className="create-job-control create-job-control--textarea">
              <span className="create-job-field-icon tone-purple">
                <DescriptionOutlinedIcon sx={{ fontSize: 18 }} />
              </span>
              <textarea
                id="create-job-description"
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

          <div className="create-job-field">
            <label htmlFor="create-job-worktype">{t.workTypeLabel}</label>
            <div className="create-job-control">
              <span className="create-job-field-icon tone-blue">
                <BusinessCenterOutlinedIcon sx={{ fontSize: 18 }} />
              </span>
              <select
                id="create-job-worktype"
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

          <div className="create-job-row">
            <div className="create-job-field">
              <label htmlFor="create-job-salary-down">{t.salaryDownLabel}</label>
              <div className="create-job-control">
                <span className="create-job-field-icon tone-purple">
                  <PaymentsOutlinedIcon sx={{ fontSize: 18 }} />
                </span>
                <input
                  id="create-job-salary-down"
                  type="number"
                  min="0"
                  placeholder={t.salaryDownPlaceholder}
                  value={salaryRangeDown}
                  onChange={(event) => setSalaryRangeDown(event.target.value)}
                />
              </div>
            </div>

            <div className="create-job-field">
              <label htmlFor="create-job-salary-top">{t.salaryTopLabel}</label>
              <div className="create-job-control">
                <span className="create-job-field-icon tone-purple">
                  <PaymentsOutlinedIcon sx={{ fontSize: 18 }} />
                </span>
                <input
                  id="create-job-salary-top"
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
              onClick={() => navigate('/home')}
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

export default CreateJobPage
