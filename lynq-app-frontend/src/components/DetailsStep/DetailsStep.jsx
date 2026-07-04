import { useEffect, useRef, useState } from 'react'
import PersonOutlineRoundedIcon from '@mui/icons-material/PersonOutlineRounded'
import BadgeOutlinedIcon from '@mui/icons-material/BadgeOutlined'
import CakeOutlinedIcon from '@mui/icons-material/CakeOutlined'
import MailOutlineRoundedIcon from '@mui/icons-material/MailOutlineRounded'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import ErrorOutlineRoundedIcon from '@mui/icons-material/ErrorOutlineRounded'
import useRegister from '../../hooks/useRegister'
import useRegisterSubmit from '../../hooks/useRegisterSubmit'
import registrationService from '../../services/registrationService'
import authService from '../../services/authService'
import DatePicker from '../DatePicker/DatePicker'
import Toast from '../Toast/Toast'
import StepIndicator from '../StepIndicator/StepIndicator'
import strings from '../../i18n'
import './DetailsStep.css'

const isEmail = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)

// The shared "user form" — same fields for candidates and companies. It is the
// final step for candidates (submits the registration) and an intermediate step
// for companies (advances to the owner/company steps). Wizard state lives in
// RegisterContext, so values persist while sliding between steps.
const DetailsStep = ({ active, isLast, stepNumber, totalSteps }) => {
  const t = strings.register
  const td = t.details
  const { data, updateData, next, back, setFooter } = useRegister()
  const { submitting, toast, setToast, run } = useRegisterSubmit()

  const [name, setName] = useState(data.name || '')
  const [username, setUsername] = useState(data.username || '')
  const [dob, setDob] = useState(data.dob || '')
  const [email, setEmail] = useState(data.email || '')
  const [password, setPassword] = useState(data.password || '')
  const [confirm, setConfirm] = useState(data.password || '')
  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState({})

  // Live availability of username/email, checked against IAM as the user leaves
  // the field. status: 'idle' | 'checking' | 'available' | 'taken'; reason holds
  // the server message when taken.
  const [check, setCheck] = useState({
    username: { status: 'idle', reason: '' },
    email: { status: 'idle', reason: '' },
  })

  // Ask IAM whether the value is available once the field loses focus. Skips the
  // call for empty/malformed input (submit-time validation covers those) and
  // fails open on network errors so a flaky check never blocks the user.
  const runAvailabilityCheck = async (field, rawValue, checkFn, isWellFormed) => {
    const value = rawValue.trim()
    if (!value || !isWellFormed(value)) {
      setCheck((prev) => ({ ...prev, [field]: { status: 'idle', reason: '' } }))
      return
    }
    setCheck((prev) => ({ ...prev, [field]: { status: 'checking', reason: '' } }))
    try {
      const result = await checkFn(value)
      setCheck((prev) => ({
        ...prev,
        [field]: result?.valid
          ? { status: 'available', reason: '' }
          : { status: 'taken', reason: result?.reason ?? '' },
      }))
    } catch {
      setCheck((prev) => ({ ...prev, [field]: { status: 'idle', reason: '' } }))
    }
  }

  const handleUsernameBlur = () =>
    runAvailabilityCheck(
      'username',
      username,
      authService.check_username,
      (value) => value.length >= 3 && value.length <= 20,
    )

  const handleEmailBlur = () =>
    runAvailabilityCheck('email', email, authService.check_email, isEmail)

  // Editing a field clears its prior verdict so a stale "taken/available" badge
  // never lingers over new input; the next blur re-checks.
  const resetCheck = (field) =>
    setCheck((prev) => ({ ...prev, [field]: { status: 'idle', reason: '' } }))

  // Combined per-field error: local format errors take precedence, then a
  // server "taken" verdict (with its reason, falling back to a generic message).
  const usernameError =
    fieldErrors.username ||
    (check.username.status === 'taken'
      ? check.username.reason || td.errors.usernameTaken
      : '')
  const emailError =
    fieldErrors.email ||
    (check.email.status === 'taken' ? check.email.reason || td.errors.emailTaken : '')

  // Right-aligned status badge inside the input: spinner while checking, green
  // tick when available, red alert when taken.
  const renderCheckStatus = (field) => {
    const { status } = check[field]
    if (status === 'checking')
      return <span className="details-status details-status-checking" aria-hidden="true" />
    if (status === 'available')
      return (
        <CheckCircleRoundedIcon
          className="details-status details-status-ok"
          sx={{ fontSize: 16 }}
        />
      )
    if (status === 'taken')
      return (
        <ErrorOutlineRoundedIcon
          className="details-status details-status-error"
          sx={{ fontSize: 16 }}
        />
      )
    return null
  }

  const validate = () => {
    const errors = {}
    if (!name.trim()) errors.name = td.errors.nameRequired
    if (!username.trim()) errors.username = td.errors.usernameRequired
    else if (username.trim().length < 3 || username.trim().length > 20)
      errors.username = td.errors.usernameLength
    // Birth date expected as yyyy-mm-dd and not in the future.
    if (!dob) errors.dob = td.errors.dobRequired
    else if (!/^\d{4}-\d{2}-\d{2}$/.test(dob) || Number.isNaN(new Date(dob).getTime()))
      errors.dob = td.errors.dobInvalid
    else if (new Date(dob) > new Date()) errors.dob = td.errors.dobInvalid
    if (!email.trim()) errors.email = td.errors.emailRequired
    else if (!isEmail(email.trim())) errors.email = td.errors.emailInvalid
    if (!password) errors.password = td.errors.passwordRequired
    else if (password.length < 8) errors.password = td.errors.passwordTooShort
    if (confirm !== password) errors.confirm = td.errors.confirmMismatch
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const persist = () => updateData({ name, username, dob, email, password })

  // Candidates finish here (create the auth user + candidate profile); companies
  // advance to the owner/company steps.
  const runPrimary = () => {
    if (!validate()) return
    // Block on a username/email already flagged as taken by the blur check.
    if (check.username.status === 'taken' || check.email.status === 'taken') return
    persist()
    if (isLast) {
      run(() =>
        registrationService.register_candidate({
          username,
          email,
          password,
          fullName: name,
          birthDate: dob,
        }),
      )
    } else {
      next()
    }
  }

  // Keep a live reference so the footer button (registered once below) always
  // runs the latest closure with current field values.
  const primaryActionRef = useRef(runPrimary)
  useEffect(() => {
    primaryActionRef.current = runPrimary
  })

  // Drive the shared footer while this is the active step. The primary action is
  // "Crear cuenta" (submit) on the last step, otherwise "Siguiente" (advance).
  useEffect(() => {
    if (!active) return
    setFooter({
      secondary: { label: t.back, onClick: back, disabled: submitting },
      primary: {
        label: isLast ? td.submit : t.next,
        disabled: submitting,
        onClick: () => primaryActionRef.current(),
      },
    })
  }, [active, isLast, submitting, back, setFooter, t.back, t.next, td.submit])

  const handleFormSubmit = (event) => {
    event.preventDefault()
    primaryActionRef.current()
  }

  return (
    <div className="details-step">
      <StepIndicator
        current={stepNumber}
        total={totalSteps}
        className="step-indicator--end"
      />
      <form className="details-form" onSubmit={handleFormSubmit} noValidate>
        <div className="details-field">
          <label htmlFor="reg-name">{td.nameLabel}</label>
          <div className="details-control">
            <span className="details-field-icon tone-blue">
              <PersonOutlineRoundedIcon sx={{ fontSize: 18 }} />
            </span>
            <input
              id="reg-name"
              placeholder={td.namePlaceholder}
              value={name}
              aria-invalid={Boolean(fieldErrors.name)}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          {fieldErrors.name && (
            <p className="details-error" role="alert">{fieldErrors.name}</p>
          )}
        </div>

        <div className="details-field">
          <label htmlFor="reg-username">{td.usernameLabel}</label>
          <div className="details-control">
            <span className="details-field-icon tone-purple">
              <BadgeOutlinedIcon sx={{ fontSize: 18 }} />
            </span>
            <div className="details-input-wrap">
              <input
                id="reg-username"
                placeholder={td.usernamePlaceholder}
                value={username}
                aria-invalid={Boolean(usernameError)}
                onChange={(event) => {
                  setUsername(event.target.value)
                  resetCheck('username')
                }}
                onBlur={handleUsernameBlur}
              />
              {renderCheckStatus('username')}
            </div>
          </div>
          {usernameError && (
            <p className="details-error" role="alert">{usernameError}</p>
          )}
          {check.username.status === 'available' && (
            <p className="details-ok" role="status">{td.usernameAvailable}</p>
          )}
        </div>

        <div className="details-field">
          <label htmlFor="reg-dob">{td.dobLabel}</label>
          <div className="details-control">
            <span className="details-field-icon tone-blue">
              <CakeOutlinedIcon sx={{ fontSize: 18 }} />
            </span>
            <DatePicker
              id="reg-dob"
              value={dob}
              onChange={setDob}
              placeholder={td.dobPlaceholder}
              ariaInvalid={Boolean(fieldErrors.dob)}
              disableFuture
            />
          </div>
          {fieldErrors.dob && (
            <p className="details-error" role="alert">{fieldErrors.dob}</p>
          )}
        </div>

        <div className="details-field">
          <label htmlFor="reg-email">{td.emailLabel}</label>
          <div className="details-control">
            <span className="details-field-icon tone-blue">
              <MailOutlineRoundedIcon sx={{ fontSize: 18 }} />
            </span>
            <div className="details-input-wrap">
              <input
                id="reg-email"
                type="email"
                placeholder={td.emailPlaceholder}
                value={email}
                aria-invalid={Boolean(emailError)}
                onChange={(event) => {
                  setEmail(event.target.value)
                  resetCheck('email')
                }}
                onBlur={handleEmailBlur}
              />
              {renderCheckStatus('email')}
            </div>
          </div>
          {emailError && (
            <p className="details-error" role="alert">{emailError}</p>
          )}
          {check.email.status === 'available' && (
            <p className="details-ok" role="status">{td.emailAvailable}</p>
          )}
        </div>

        <div className="details-field">
          <label htmlFor="reg-password">{td.passwordLabel}</label>
          <div className="details-control">
            <span className="details-field-icon tone-purple">
              <LockOutlinedIcon sx={{ fontSize: 18 }} />
            </span>
            <div className="details-input-wrap">
              <input
                id="reg-password"
                type={showPassword ? 'text' : 'password'}
                placeholder={td.passwordPlaceholder}
                value={password}
                aria-invalid={Boolean(fieldErrors.password)}
                onChange={(event) => setPassword(event.target.value)}
              />
              <button
                type="button"
                className="details-eye"
                aria-label={showPassword ? strings.login.hidePassword : strings.login.showPassword}
                onClick={() => setShowPassword((prev) => !prev)}
              >
                {showPassword ? (
                  <VisibilityOffOutlinedIcon sx={{ fontSize: 16 }} />
                ) : (
                  <VisibilityOutlinedIcon sx={{ fontSize: 16 }} />
                )}
              </button>
            </div>
          </div>
          {fieldErrors.password && (
            <p className="details-error" role="alert">{fieldErrors.password}</p>
          )}
        </div>

        <div className="details-field">
          <label htmlFor="reg-confirm">{td.confirmLabel}</label>
          <div className="details-control">
            <span className="details-field-icon tone-blue">
              <LockOutlinedIcon sx={{ fontSize: 18 }} />
            </span>
            <input
              id="reg-confirm"
              type={showPassword ? 'text' : 'password'}
              placeholder={td.confirmPlaceholder}
              value={confirm}
              aria-invalid={Boolean(fieldErrors.confirm)}
              onChange={(event) => setConfirm(event.target.value)}
            />
          </div>
          {fieldErrors.confirm && (
            <p className="details-error" role="alert">{fieldErrors.confirm}</p>
          )}
        </div>

        {/* Hidden submit keeps Enter-to-submit working; the visible action
            buttons live in the static RegisterFooter. */}
        <button type="submit" className="details-hidden-submit" aria-hidden="true" tabIndex={-1} />
      </form>

      <Toast message={toast} onClose={() => setToast(null)} />
    </div>
  )
}

export default DetailsStep
