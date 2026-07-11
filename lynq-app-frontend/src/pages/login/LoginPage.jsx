import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import PersonOutlineRoundedIcon from '@mui/icons-material/PersonOutlineRounded'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined'
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined'
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded'
import authService from '../../services/authService'
import userService from '../../services/userService'
import useAuth from '../../hooks/useAuth'
import requestUuidUtil from '../../utils/requestUuid'
import securedFetch from '../../utils/securedFetch'
import Toast from '../../components/Toast/Toast'
import GoogleIcon from '../../components/GoogleIcon/GoogleIcon'
import strings from '../../i18n'
import './LoginPage.css'

const isEmail = (identifier) => identifier.includes('@')

const LoginPage = () => {
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(false)
  const [toast, setToast] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const navigate = useNavigate()
  const { login } = useAuth()
  const t = strings.login

  const validate = () => {
    const errors = {}
    if (!identifier.trim()) errors.identifier = t.errors.identifierRequired
    if (!password.trim()) errors.password = t.errors.passwordRequired
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleLogin = async (event) => {
    event.preventDefault()
    setToast(null)
    if (!validate()) return
    setSubmitting(true)
    try {
      // One correlation id for the whole login functionality: the IAM auth call
      // and the app-backend profile fetch below share it so they trace together.
      const requestUuid = requestUuidUtil.newRequestUuid()
      const auth = isEmail(identifier)
        ? await authService.email_authenticate(identifier, password, requestUuid)
        : await authService.user_authenticate(identifier, password, requestUuid)

      // Enrich the session with the user's profile (fullName, avatar, …).
      // Non-fatal: if the profile lookup fails, the user is still logged in.
      let profile = null
      try {
        // Fresh token from the login just above; share the requestUuid so the
        // auth call and this profile fetch trace as one flow.
        profile = await userService.get_user(
          securedFetch.tokenFetcher(auth.accessToken, requestUuid),
        )
      } catch {
        // No profile yet, or the backend is unreachable — proceed without it.
      }

      login(auth, rememberMe, profile)
      navigate('/home')
    } catch (err) {
      setToast(err?.message || t.errors.loginFailed)
    } finally {
      setSubmitting(false)
    }
  }

  const handleRegister = () => {
    navigate('/register')
  }

  return (
    <div className="login-bg">
      <div className="login-dots login-dots-tl" />
      <div className="login-dots login-dots-br" />

      <main className="login-card">
        <h1 className="login-title">{t.title}</h1>
        <p className="login-subtitle">{t.subtitle}</p>

        <form className="login-form" onSubmit={handleLogin}>
          <div className="login-field">
            <span className="login-field-icon tone-blue">
              <PersonOutlineRoundedIcon sx={{ fontSize: 22 }} />
            </span>
            <div className="login-field-body">
              <label htmlFor="identifier">{t.identifierLabel}</label>
              <input
                id="identifier"
                placeholder={t.identifierPlaceholder}
                value={identifier}
                aria-invalid={Boolean(fieldErrors.identifier)}
                onChange={(event) => setIdentifier(event.target.value)}
              />
              {fieldErrors.identifier && (
                <p className="login-error" role="alert">
                  {fieldErrors.identifier}
                </p>
              )}
            </div>
          </div>

          <div className="login-field">
            <span className="login-field-icon tone-purple">
              <LockOutlinedIcon sx={{ fontSize: 22 }} />
            </span>
            <div className="login-field-body">
              <label htmlFor="password">{t.passwordLabel}</label>
              <div className="login-input-wrap">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder={t.passwordPlaceholder}
                  value={password}
                  aria-invalid={Boolean(fieldErrors.password)}
                  onChange={(event) => setPassword(event.target.value)}
                />
                <button
                  type="button"
                  className="login-eye"
                  aria-label={showPassword ? t.hidePassword : t.showPassword}
                  onClick={() => setShowPassword((prev) => !prev)}
                >
                  {showPassword ? (
                    <VisibilityOffOutlinedIcon sx={{ fontSize: 20 }} />
                  ) : (
                    <VisibilityOutlinedIcon sx={{ fontSize: 20 }} />
                  )}
                </button>
              </div>
              {fieldErrors.password && (
                <p className="login-error" role="alert">
                  {fieldErrors.password}
                </p>
              )}
            </div>
          </div>

          <div className="login-options">
            <label className="login-remember">
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(event) => setRememberMe(event.target.checked)}
              />
              {t.rememberMe}
            </label>
            <button type="button" className="login-forgot">
              {t.forgotPassword}
            </button>
          </div>

          <div className="login-actions">
            <button type="submit" disabled={submitting}>
              {t.loginButton}
              <ArrowForwardRoundedIcon sx={{ fontSize: 16 }} />
            </button>
            <button
              type="button"
              className="login-secondary"
              onClick={handleRegister}
              disabled={submitting}
            >
              {t.registerButton}
            </button>
          </div>
        </form>

        <div className="login-divider">
          <span>{t.orDivider}</span>
        </div>

        <p className="login-continue">{t.continueWith}</p>

        <div className="login-social">
          <button type="button" className="login-social-btn">
            <GoogleIcon />
            {t.google}
          </button>
        </div>

        <p className="login-security">
          <ShieldOutlinedIcon sx={{ fontSize: 16 }} />
          {t.securityNote}
        </p>
      </main>

      <Toast message={toast} onClose={() => setToast(null)} />
    </div>
  )
}

export default LoginPage
