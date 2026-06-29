import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuth from './useAuth'
import strings from '../i18n'

// Drives the final registration submit from whichever step is last (DetailsStep
// for candidates, CompanyDetailsStep for companies). Centralizes the submitting
// flag, error toast and post-success handling; the caller supplies the actual
// async action (e.g. registrationService.register_candidate / register_company),
// which resolves to the auth response (UserRestResponse with tokens).
const useRegisterSubmit = () => {
  const navigate = useNavigate()
  const { login } = useAuth()
  const [submitting, setSubmitting] = useState(false)
  const [toast, setToast] = useState(null)

  // Returns true on success, false if the action threw. On success it stores the
  // authenticated session (same as login) and sends the new user to /home.
  const run = async (action) => {
    setToast(null)
    setSubmitting(true)
    try {
      const auth = await action()
      login(auth)
      navigate('/home')
      return true
    } catch (err) {
      setToast(err?.message || strings.login.errors.registerFailed)
      return false
    } finally {
      setSubmitting(false)
    }
  }

  return { submitting, toast, setToast, run }
}

export default useRegisterSubmit
