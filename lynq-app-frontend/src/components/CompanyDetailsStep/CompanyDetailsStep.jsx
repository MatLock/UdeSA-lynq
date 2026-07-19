import { useEffect, useRef, useState } from 'react'
import ApartmentRoundedIcon from '@mui/icons-material/ApartmentRounded'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined'
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined'
import useRegister from '../../hooks/useRegister'
import useRegisterSubmit from '../../hooks/useRegisterSubmit'
import registrationService from '../../services/registrationService'
import companyService from '../../services/companyService'
import securedFetch from '../../utils/securedFetch'
import Toast from '../Toast/Toast'
import StepIndicator from '../StepIndicator/StepIndicator'
import strings from '../../i18n'
import './CompanyDetailsStep.css'

// Company final step (step 4): the company fields. The auth credentials and
// owner profile were collected and persisted by the earlier steps, so this step
// adds the company details and submits the whole registration (auth user +
// owner profile + company) via registrationService.register_company.
//
// The company logo is an optional file field (where a URL was expected before).
// It uploads to S3 only AFTER the company exists — the pre-signed-URL endpoint
// needs the created company — so the upload runs after register_company
// resolves, using the fresh access token.
const CompanyDetailsStep = ({ active, stepNumber, totalSteps }) => {
  const t = strings.register
  const cd = t.companyDetails
  const { data, updateData, back, setFooter } = useRegister()
  const { submitting, toast, setToast, run } = useRegisterSubmit()

  const [companyName, setCompanyName] = useState(data.companyName || '')
  const [companyAbout, setCompanyAbout] = useState(data.companyAbout || '')
  const [companySize, setCompanySize] = useState(data.companySize || '')
  // The picked logo File, held for upload-after-register (see submit).
  const [logoFile, setLogoFile] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})

  const validate = () => {
    const errors = {}
    if (!companyName.trim()) errors.companyName = cd.errors.nameRequired
    if (!companyAbout.trim()) errors.companyAbout = cd.errors.aboutRequired
    const size = Number(companySize)
    if (!companySize) errors.companySize = cd.errors.sizeRequired
    else if (!Number.isInteger(size) || size <= 0) errors.companySize = cd.errors.sizeInvalid
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleLogoChange = (event) => {
    // The logo is optional; keep the picked File (or clear it) for upload on submit.
    setLogoFile(event.target.files?.[0] ?? null)
  }

  const submit = () => {
    if (!validate()) return
    updateData({ companyName, companyAbout, companySize })
    // Credentials + owner profile come from wizard state set by the prior steps.
    run(async () => {
      const { auth } = await registrationService.register_company({
        username: data.username,
        email: data.email,
        password: data.password,
        fullName: data.name,
        currentPosition: data.currentPosition,
        userAbout: data.userAbout,
        birthDate: data.dob,
        linkedinUrl: data.linkedinUrl || undefined,
        companyName,
        companyAbout,
        companySize: Number(companySize),
      })

      // Upload the logo now that the company exists. Best-effort: the account is
      // already created, so a failed logo upload must not fail registration.
      if (logoFile) {
        try {
          const preSignedUrl = await companyService.generate_company_image_upload_url(
            securedFetch.tokenFetcher(auth.accessToken),
            logoFile.name,
          )
          await companyService.upload_company_image(preSignedUrl, logoFile)
        } catch {
          // Keep going — the logo can be set later from the company page.
        }
      }

      return auth
    })
  }

  // Live reference so the footer button always runs the latest closure.
  const submitRef = useRef(submit)
  useEffect(() => {
    submitRef.current = submit
  })

  useEffect(() => {
    if (!active) return
    setFooter({
      secondary: { label: t.back, onClick: back, disabled: submitting },
      primary: {
        label: t.details.submit,
        disabled: submitting,
        onClick: () => submitRef.current(),
      },
    })
  }, [active, submitting, back, setFooter, t.back, t.details.submit])

  const handleFormSubmit = (event) => {
    event.preventDefault()
    submit()
  }

  return (
    <div className="company-step">
      <StepIndicator
        current={stepNumber}
        total={totalSteps}
        className="step-indicator--end"
      />
      <form className="company-form" onSubmit={handleFormSubmit} noValidate>
        <div className="company-field">
          <label htmlFor="reg-company-name">{cd.nameLabel}</label>
          <div className="company-control">
            <span className="company-field-icon tone-blue">
              <ApartmentRoundedIcon sx={{ fontSize: 18 }} />
            </span>
            <input
              id="reg-company-name"
              placeholder={cd.namePlaceholder}
              value={companyName}
              aria-invalid={Boolean(fieldErrors.companyName)}
              onChange={(event) => setCompanyName(event.target.value)}
            />
          </div>
          {fieldErrors.companyName && (
            <p className="company-error" role="alert">{fieldErrors.companyName}</p>
          )}
        </div>

        <div className="company-field">
          <label htmlFor="reg-company-about">{cd.aboutLabel}</label>
          <div className="company-control">
            <span className="company-field-icon tone-purple">
              <InfoOutlinedIcon sx={{ fontSize: 18 }} />
            </span>
            <input
              id="reg-company-about"
              placeholder={cd.aboutPlaceholder}
              value={companyAbout}
              aria-invalid={Boolean(fieldErrors.companyAbout)}
              onChange={(event) => setCompanyAbout(event.target.value)}
            />
          </div>
          {fieldErrors.companyAbout && (
            <p className="company-error" role="alert">{fieldErrors.companyAbout}</p>
          )}
        </div>

        <div className="company-field">
          <label htmlFor="reg-company-size">{cd.sizeLabel}</label>
          <div className="company-control">
            <span className="company-field-icon tone-blue">
              <GroupsOutlinedIcon sx={{ fontSize: 18 }} />
            </span>
            <input
              id="reg-company-size"
              type="number"
              min="1"
              step="1"
              placeholder={cd.sizePlaceholder}
              value={companySize}
              aria-invalid={Boolean(fieldErrors.companySize)}
              onChange={(event) => setCompanySize(event.target.value)}
            />
          </div>
          {fieldErrors.companySize && (
            <p className="company-error" role="alert">{fieldErrors.companySize}</p>
          )}
        </div>

        <div className="company-field">
          <label htmlFor="reg-company-logo">{cd.logoLabel}</label>
          <div className="company-control">
            <span className="company-field-icon tone-purple">
              <ImageOutlinedIcon sx={{ fontSize: 18 }} />
            </span>
            <input
              id="reg-company-logo"
              type="file"
              accept="image/*"
              className="company-logo-input"
              onChange={handleLogoChange}
            />
          </div>
        </div>

        {/* Hidden submit keeps Enter-to-submit working; visible buttons are in
            the static RegisterFooter. */}
        <button type="submit" className="company-hidden-submit" aria-hidden="true" tabIndex={-1} />
      </form>

      <Toast message={toast} onClose={() => setToast(null)} />
    </div>
  )
}

export default CompanyDetailsStep
