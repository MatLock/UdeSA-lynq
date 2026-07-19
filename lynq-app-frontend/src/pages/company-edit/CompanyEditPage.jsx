import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import ApartmentRoundedIcon from '@mui/icons-material/ApartmentRounded'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import CompanyIcon from '../../components/CompanyIcon/CompanyIcon.jsx'
import LoadingOverlay from '../../components/LoadingOverlay/LoadingOverlay'
import Spinner from '../../components/Spinner/Spinner'
import Toast from '../../components/Toast/Toast'
import useApi from '../../hooks/useApi'
import companyService from '../../services/companyService'
import fileToDataUrl from '../../utils/fileToDataUrl'
import strings from '../../i18n'
import './CompanyEditPage.css'

// Company editor for the authenticated owner, reached only from the sidebar at
// /company/:companyId/edit. On landing it fetches the company
// (GET /company/{companyId}) and pre-fills the form; the editable fields mirror
// the backend UpdateCompanyRequest (name, about, size) plus the logo, which
// uploads separately via the pre-signed-URL flow. Reuses the auth card design +
// field styling shared with the profile editor. (The public, read-only company
// profile lives at /company/:companyId — see CompanyDetailPage.)
const CompanyEditPage = () => {
  const t = strings.pages.company
  const { companyId } = useParams()
  const { authFetch } = useApi()

  // Initial fetch state; the form is shown once the company loads.
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  // Blocks the screen while the save request is in flight.
  const [saving, setSaving] = useState(false)
  // Success/error feedback after a save attempt: { type, message }.
  const [toast, setToast] = useState(null)

  const [name, setName] = useState('')
  const [about, setAbout] = useState('')
  const [size, setSize] = useState('')
  // Shown in the logo tile: the company's current (pre-signed) logo URL, or a
  // freshly picked upload as a data URL.
  const [imagePreview, setImagePreview] = useState('')
  // Blocks the screen while the picked logo uploads to S3.
  const [uploading, setUploading] = useState(false)
  const [imageError, setImageError] = useState('')
  const fileInputRef = useRef(null)

  // Fetch the company on landing and pre-fill the form. Re-runs if the id changes.
  useEffect(() => {
    if (!companyId) return
    let cancelled = false
    const loadCompany = async () => {
      setLoading(true)
      setLoadError('')
      try {
        const company = await companyService.get_company_detail(authFetch, companyId)
        if (cancelled) return
        setName(company.name ?? '')
        setAbout(company.about ?? '')
        setSize(company.size != null ? String(company.size) : '')
        setImagePreview(company.profileImageUrl ?? '')
      } catch (error) {
        if (!cancelled) {
          setLoadError(error.reason ?? error.message ?? t.loadError)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    loadCompany()
    return () => {
      cancelled = true
    }
  }, [authFetch, companyId, t.loadError])

  // Clicking the logo opens the OS file picker.
  const handleLogoClick = () => fileInputRef.current?.click()

  // Upload flow: ask the backend for a short-lived pre-signed S3 URL, PUT the
  // image bytes straight to S3, then preview the picked file locally (the
  // pre-signed URL expires after ~15 minutes).
  const handleImageChange = async (event) => {
    const file = event.target.files?.[0]
    // Reset the input so picking the same file again still fires onChange.
    event.target.value = ''
    if (!file) return

    setImageError('')
    setUploading(true)
    try {
      const preSignedUrl = await companyService.generate_company_image_upload_url(
        authFetch,
        file.name,
      )
      await companyService.upload_company_image(preSignedUrl, file)
      setImagePreview(await fileToDataUrl(file))
    } catch (error) {
      setImageError(error.reason ?? error.message ?? t.logoUploadError)
    } finally {
      setUploading(false)
    }
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setToast(null)
    setSaving(true)
    try {
      // Partial update (PATCH /company; the owner is resolved from the token).
      // Send null for a blank name/size so the backend keeps the current value
      // rather than clearing it; about may be cleared with an empty string. The
      // logo isn't part of this call — it uploads on pick.
      const updated = await companyService.update_company(authFetch, {
        name: name.trim() || null,
        about,
        size: size === '' ? null : Number(size),
      })
      setName(updated.name ?? '')
      setAbout(updated.about ?? '')
      setSize(updated.size != null ? String(updated.size) : '')
      setToast({ type: 'success', message: t.saveSuccess })
    } catch (error) {
      setToast({ type: 'error', message: error.reason ?? error.message ?? t.saveError })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="login-bg company-edit-page">
      {uploading && <LoadingOverlay label={t.logoUploading} />}
      {saving && <LoadingOverlay label={t.saving} />}
      <Toast
        message={toast?.message}
        type={toast?.type}
        onClose={() => setToast(null)}
      />
      <div className="login-dots login-dots-tl" />
      <div className="login-dots login-dots-br" />

      <main className="login-card company-edit-card">
        <h1 className="login-title">{t.title}</h1>
        <p className="login-subtitle">{t.subtitle}</p>

        {loading ? (
          <div className="company-edit-loading">
            <Spinner label={t.loading} />
          </div>
        ) : loadError ? (
          <p className="company-edit-error company-edit-load-error" role="alert">
            {loadError}
          </p>
        ) : (
          <form className="company-edit-form" onSubmit={handleSubmit} noValidate>
            <div className="company-edit-logo-field">
              <button
                type="button"
                className="company-edit-logo"
                onClick={handleLogoClick}
                aria-label={t.logoChange}
                title={t.logoChange}
                disabled={uploading}
              >
                {imagePreview ? (
                  <img src={imagePreview} alt={t.logoAlt} />
                ) : (
                  <CompanyIcon />
                )}
                <span className="company-edit-logo-edit" aria-hidden="true">
                  <EditOutlinedIcon sx={{ fontSize: 12 }} />
                </span>
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                className="company-edit-logo-input"
                onChange={handleImageChange}
              />
              <p className="company-edit-hint company-edit-logo-hint">{t.logoHint}</p>
              {imageError && (
                <p className="company-edit-error company-edit-logo-error" role="alert">
                  {imageError}
                </p>
              )}
            </div>

            <div className="company-edit-field">
              <label htmlFor="company-name">{t.nameLabel}</label>
              <div className="company-edit-control">
                <span className="company-edit-field-icon tone-blue">
                  <ApartmentRoundedIcon sx={{ fontSize: 18 }} />
                </span>
                <input
                  id="company-name"
                  placeholder={t.namePlaceholder}
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
              </div>
            </div>

            <div className="company-edit-field">
              <label htmlFor="company-about">{t.aboutLabel}</label>
              <div className="company-edit-control company-edit-control--textarea">
                <span className="company-edit-field-icon tone-purple">
                  <InfoOutlinedIcon sx={{ fontSize: 18 }} />
                </span>
                <textarea
                  id="company-about"
                  className="company-edit-textarea"
                  rows={5}
                  placeholder={t.aboutPlaceholder}
                  value={about}
                  onChange={(event) => setAbout(event.target.value)}
                />
              </div>
            </div>

            <div className="company-edit-field">
              <label htmlFor="company-size">{t.sizeLabel}</label>
              <div className="company-edit-control">
                <span className="company-edit-field-icon tone-blue">
                  <GroupsOutlinedIcon sx={{ fontSize: 18 }} />
                </span>
                <input
                  id="company-size"
                  type="number"
                  min="1"
                  placeholder={t.sizePlaceholder}
                  value={size}
                  onChange={(event) => setSize(event.target.value)}
                />
              </div>
            </div>

            <button type="submit" className="company-edit-save" disabled={saving}>
              {t.save}
            </button>
          </form>
        )}
      </main>
    </div>
  )
}

export default CompanyEditPage
