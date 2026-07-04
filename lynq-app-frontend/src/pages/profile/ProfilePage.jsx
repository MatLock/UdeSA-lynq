import { useEffect, useRef, useState } from 'react'
import PersonOutlineRoundedIcon from '@mui/icons-material/PersonOutlineRounded'
import BadgeOutlinedIcon from '@mui/icons-material/BadgeOutlined'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import GitHubIcon from '@mui/icons-material/GitHub'
import LinkedInIcon from '@mui/icons-material/LinkedIn'
import CakeOutlinedIcon from '@mui/icons-material/CakeOutlined'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import DatePicker from '../../components/DatePicker/DatePicker'
import UserIcon from '../../components/UserIcon/UserIcon'
import LoadingOverlay from '../../components/LoadingOverlay/LoadingOverlay'
import Spinner from '../../components/Spinner/Spinner'
import Toast from '../../components/Toast/Toast'
import useAuth from '../../hooks/useAuth'
import userService from '../../services/userService'
import authService from '../../services/authService'
import profileImageCache from '../../utils/profileImageCache'
import fileToDataUrl from '../../utils/fileToDataUrl'
import strings from '../../i18n'
import './ProfilePage.css'

// User profile editor. On landing it fetches the authenticated user's profile
// (GET /user) and pre-fills the form; a loading component shows meanwhile.
// Fields mirror the backend UpdateUserProfileRequest (fullName,
// userProfileImageUrl, currentPosition, about, githubUrl, linkedinUrl,
// birthDate) plus password. The update service call will be wired in later.
const ProfilePage = () => {
  const t = strings.pages.profile
  const { user, accessToken, updateUser, applyTokens } = useAuth()

  // Initial profile fetch state. Only "loading" when there's a token to fetch
  // with, so the empty form never flashes before the fetch starts.
  const [loading, setLoading] = useState(() => Boolean(accessToken))
  const [loadError, setLoadError] = useState('')
  // Blocks the screen while the save request is in flight.
  const [saving, setSaving] = useState(false)
  // Success/error feedback after a save attempt: { type, message }.
  const [toast, setToast] = useState(null)

  const [fullName, setFullName] = useState('')
  // Shown in the avatar: a cached upload if present, otherwise the user's
  // current (pre-signed) profile image URL.
  const [imagePreview, setImagePreview] = useState(
    () => profileImageCache.read(user?.id) ?? user?.profileImageUrl ?? '',
  )
  // Blocks the screen while the picked image uploads to S3.
  const [uploading, setUploading] = useState(false)
  const [imageError, setImageError] = useState('')
  const fileInputRef = useRef(null)
  const [currentPosition, setCurrentPosition] = useState('')
  const [about, setAbout] = useState('')
  const [githubUrl, setGithubUrl] = useState('')
  const [linkedinUrl, setLinkedinUrl] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [passwordError, setPasswordError] = useState('')

  // Fetch the profile on landing and pre-fill the form. Re-runs if the signed-in
  // user changes.
  useEffect(() => {
    if (!accessToken) return
    let cancelled = false
    const loadProfile = async () => {
      setLoading(true)
      setLoadError('')
      try {
        const profile = await userService.get_user(accessToken)
        if (cancelled) return
        setFullName(profile.fullName ?? '')
        setCurrentPosition(profile.currentPosition ?? '')
        setAbout(profile.about ?? '')
        setGithubUrl(profile.githubUrl ?? '')
        setLinkedinUrl(profile.linkedinUrl ?? '')
        setBirthDate(profile.birthDate ?? '')
        // Prefer the locally cached upload over the (short-lived) pre-signed URL.
        setImagePreview(
          profileImageCache.read(user?.id) ?? profile.userProfileImageUrl ?? '',
        )
      } catch (error) {
        if (!cancelled) {
          setLoadError(error.reason ?? error.message ?? strings.pages.profile.loadError)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    loadProfile()
    return () => {
      cancelled = true
    }
  }, [accessToken, user?.id])

  // Clicking the avatar opens the OS file picker.
  const handleAvatarClick = () => fileInputRef.current?.click()

  // Upload flow: ask the backend for a short-lived pre-signed S3 URL, PUT the
  // image bytes straight to S3, then cache the image locally (the pre-signed URL
  // expires after 15 minutes) and reflect it in the avatar + sidebar.
  const handleImageChange = async (event) => {
    const file = event.target.files?.[0]
    // Reset the input so picking the same file again still fires onChange.
    event.target.value = ''
    if (!file) return

    setImageError('')
    setUploading(true)
    try {
      const preSignedUrl = await userService.generate_profile_image_upload_url(
        file.name,
        accessToken,
      )
      await userService.upload_profile_image(preSignedUrl, file)

      // Cache the bytes so the avatar survives the pre-signed URL's 15-minute
      // lifetime, then show it here and in the sidebar.
      const dataUrl = await fileToDataUrl(file)
      profileImageCache.write(user?.id, dataUrl)
      setImagePreview(dataUrl)
      updateUser({ profileImageUrl: dataUrl })
    } catch (error) {
      setImageError(error.reason ?? error.message ?? t.imageUploadError)
    } finally {
      setUploading(false)
    }
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    // Require the confirmation to match before changing the password.
    if (password !== confirmPassword) {
      setPasswordError(t.passwordMismatch)
      return
    }
    setPasswordError('')
    setToast(null)
    setSaving(true)
    try {
      // Update the profile fields (PATCH /user). birthDate must be null (not an
      // empty string) when unset, since the backend parses it as a date. The
      // profile image isn't part of this call — it uploads on pick.
      const updated = await userService.update_user_profile(
        {
          fullName,
          currentPosition,
          about,
          githubUrl,
          linkedinUrl,
          birthDate: birthDate || null,
        },
        accessToken,
      )
      // Reflect the saved values in the session so the sidebar stays in sync.
      updateUser({
        fullName: updated.fullName,
        currentPosition: updated.currentPosition,
        about: updated.about,
        githubUrl: updated.githubUrl,
        linkedinUrl: updated.linkedinUrl,
        birthDate: updated.birthDate,
      })

      // Password change is a separate IAM call and rotates both tokens; only
      // run it when a new password was actually entered.
      if (password) {
        const auth = await authService.user_update_password(password, accessToken)
        applyTokens({
          accessToken: auth.accessToken,
          refreshToken: auth.refreshToken,
        })
        setPassword('')
        setConfirmPassword('')
      }

      setToast({ type: 'success', message: t.saveSuccess })
    } catch (error) {
      setToast({ type: 'error', message: error.reason ?? error.message ?? t.saveError })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="login-bg profile-page">
      {uploading && <LoadingOverlay label={t.imageUploading} />}
      {saving && <LoadingOverlay label={t.saving} />}
      <Toast
        message={toast?.message}
        type={toast?.type}
        onClose={() => setToast(null)}
      />
      <div className="login-dots login-dots-tl" />
      <div className="login-dots login-dots-br" />

      <main className="login-card profile-card">
        <h1 className="login-title">{t.title}</h1>
        <p className="login-subtitle">{t.subtitle}</p>

        {loading ? (
          <div className="profile-loading">
            <Spinner label={t.loading} />
          </div>
        ) : (
        <form className="profile-form" onSubmit={handleSubmit} noValidate>
          {loadError && (
            <p className="profile-error profile-load-error" role="alert">
              {loadError}
            </p>
          )}
          <div className="profile-avatar-field">
            <button
              type="button"
              className="profile-avatar"
              onClick={handleAvatarClick}
              aria-label={t.imageChange}
              title={t.imageChange}
              disabled={uploading}
            >
              {imagePreview ? (
                <img src={imagePreview} alt={t.imageAlt} />
              ) : (
                <UserIcon />
              )}
              <span className="profile-avatar-edit" aria-hidden="true">
                <EditOutlinedIcon sx={{ fontSize: 12 }} />
              </span>
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="profile-avatar-input"
              onChange={handleImageChange}
            />
            <p className="profile-hint profile-avatar-hint">{t.imageHint}</p>
            {imageError && (
              <p className="profile-error profile-avatar-error" role="alert">
                {imageError}
              </p>
            )}
          </div>

          <div className="profile-field">
            <label htmlFor="profile-fullname">{t.fullNameLabel}</label>
            <div className="profile-control">
              <span className="profile-field-icon tone-blue">
                <PersonOutlineRoundedIcon sx={{ fontSize: 18 }} />
              </span>
              <input
                id="profile-fullname"
                placeholder={t.fullNamePlaceholder}
                value={fullName}
                onChange={(event) => setFullName(event.target.value)}
              />
            </div>
          </div>

          <div className="profile-field">
            <label htmlFor="profile-position">{t.positionLabel}</label>
            <div className="profile-control">
              <span className="profile-field-icon tone-blue">
                <BadgeOutlinedIcon sx={{ fontSize: 18 }} />
              </span>
              <input
                id="profile-position"
                placeholder={t.positionPlaceholder}
                value={currentPosition}
                onChange={(event) => setCurrentPosition(event.target.value)}
              />
            </div>
          </div>

          <div className="profile-field">
            <label htmlFor="profile-about">{t.aboutLabel}</label>
            <div className="profile-control profile-control--textarea">
              <span className="profile-field-icon tone-purple">
                <InfoOutlinedIcon sx={{ fontSize: 18 }} />
              </span>
              <textarea
                id="profile-about"
                className="profile-textarea"
                rows={5}
                placeholder={t.aboutPlaceholder}
                value={about}
                onChange={(event) => setAbout(event.target.value)}
              />
            </div>
          </div>

          <div className="profile-field">
            <label htmlFor="profile-github">{t.githubLabel}</label>
            <div className="profile-control">
              <span className="profile-field-icon tone-blue">
                <GitHubIcon sx={{ fontSize: 18 }} />
              </span>
              <input
                id="profile-github"
                type="url"
                placeholder={t.githubPlaceholder}
                value={githubUrl}
                onChange={(event) => setGithubUrl(event.target.value)}
              />
            </div>
          </div>

          <div className="profile-field">
            <label htmlFor="profile-linkedin">{t.linkedinLabel}</label>
            <div className="profile-control">
              <span className="profile-field-icon tone-purple">
                <LinkedInIcon sx={{ fontSize: 18 }} />
              </span>
              <input
                id="profile-linkedin"
                type="url"
                placeholder={t.linkedinPlaceholder}
                value={linkedinUrl}
                onChange={(event) => setLinkedinUrl(event.target.value)}
              />
            </div>
          </div>

          <div className="profile-field">
            <label htmlFor="profile-birthdate">{t.birthDateLabel}</label>
            <div className="profile-control">
              <span className="profile-field-icon tone-blue">
                <CakeOutlinedIcon sx={{ fontSize: 18 }} />
              </span>
              <DatePicker
                id="profile-birthdate"
                value={birthDate}
                onChange={setBirthDate}
                placeholder={t.birthDatePlaceholder}
                disableFuture
              />
            </div>
          </div>

          <div className="profile-field">
            <label htmlFor="profile-password">{t.passwordLabel}</label>
            <div className="profile-control">
              <span className="profile-field-icon tone-purple">
                <LockOutlinedIcon sx={{ fontSize: 18 }} />
              </span>
              <div className="profile-input-wrap">
                <input
                  id="profile-password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder={t.passwordPlaceholder}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
                <button
                  type="button"
                  className="profile-eye"
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
            <p className="profile-hint">{t.passwordHint}</p>
          </div>

          <div className="profile-field">
            <label htmlFor="profile-confirm-password">{t.confirmPasswordLabel}</label>
            <div className="profile-control">
              <span className="profile-field-icon tone-blue">
                <LockOutlinedIcon sx={{ fontSize: 18 }} />
              </span>
              <input
                id="profile-confirm-password"
                type={showPassword ? 'text' : 'password'}
                placeholder={t.confirmPasswordPlaceholder}
                value={confirmPassword}
                aria-invalid={Boolean(passwordError)}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
            </div>
            {passwordError && (
              <p className="profile-error" role="alert">{passwordError}</p>
            )}
          </div>

          <button type="submit" className="profile-save" disabled={saving}>
            {t.save}
          </button>
        </form>
        )}
      </main>
    </div>
  )
}

export default ProfilePage
