import { createContext, useCallback, useMemo, useState } from 'react'

// Holds the resume-creation wizard's data, its current step, AND the config for
// the static footer (the action buttons below the carousel). It mirrors
// RegisterContext on purpose: the resume flow is the same in-place carousel as
// registration, so state lives here to survive sliding between steps and to let
// the active step drive the shared footer.
//
// The callbacks are memoized with useCallback so step effects that depend on them
// don't re-run (and re-set the footer) on every provider render.
const ResumeWizardContext = createContext(null)

// Empty entry shapes, matching the resume JSON the backend stores. Steps use
// these when the user adds a row, so a freshly added entry always has every key
// the payload expects (rather than growing keys as fields are typed into).
//
// `technologies` is kept even though the form no longer asks for it: the stored
// shape declares the field, and a resume extracted from an uploaded document does
// populate it — so the payload always carries the key, empty for form-built ones.
const EMPTY_EXPERIENCE = {
  company: '',
  position: '',
  location: '',
  start_date: '',
  end_date: '',
  is_current: false,
  description: '',
  achievements: [],
  technologies: [],
}

const EMPTY_EDUCATION = {
  institution: '',
  degree: '',
  field_of_study: '',
  start_date: '',
  end_date: '',
  is_current: false,
  description: '',
}

const EMPTY_LANGUAGE = { language: '', proficiency: '' }

const EMPTY_CERTIFICATION = {
  name: '',
  issuer: '',
  issue_date: '',
  credential_id: '',
}

const EMPTY_PROJECT = { name: '', description: '', technologies: [], url: '' }

const ResumeWizardProvider = ({ children }) => {
  const [data, setData] = useState({})
  const [step, setStep] = useState(0)
  // Footer config set by the active step: { primary, secondary } where each is
  // { label, onClick, disabled }. null hides the actions.
  const [footer, setFooter] = useState(null)

  const updateData = useCallback(
    (partial) => setData((prev) => ({ ...prev, ...partial })),
    [],
  )

  const next = useCallback(() => setStep((prev) => prev + 1), [])
  const back = useCallback(() => setStep((prev) => Math.max(0, prev - 1)), [])
  const goTo = useCallback((target) => setStep(Math.max(0, target)), [])

  const reset = useCallback(() => {
    setData({})
    setStep(0)
  }, [])

  const value = useMemo(
    () => ({ data, updateData, step, next, back, goTo, reset, footer, setFooter }),
    [data, updateData, step, next, back, goTo, reset, footer, setFooter],
  )

  return (
    <ResumeWizardContext.Provider value={value}>
      {children}
    </ResumeWizardContext.Provider>
  )
}

export {
  ResumeWizardContext,
  EMPTY_EXPERIENCE,
  EMPTY_EDUCATION,
  EMPTY_LANGUAGE,
  EMPTY_CERTIFICATION,
  EMPTY_PROJECT,
}
export default ResumeWizardProvider
