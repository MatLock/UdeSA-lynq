import { createContext, useCallback, useMemo, useState } from 'react'

// Holds the registration wizard's data, its current step, AND the config for the
// static footer (the action buttons that live below the carousel). State lives
// here so the form data and carousel position survive sliding between steps, and
// so the active step can drive the shared footer.
//
// The callbacks are memoized with useCallback so step effects that depend on them
// don't re-run (and re-set the footer) on every provider render.
const RegisterContext = createContext(null)

const RegisterProvider = ({ children }) => {
  const [data, setData] = useState({})
  const [step, setStep] = useState(0)
  // Footer config set by the active step: { primary, secondary } where each is
  // { label, onClick, disabled }. null hides the footer.
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
    <RegisterContext.Provider value={value}>
      {children}
    </RegisterContext.Provider>
  )
}

export { RegisterContext }
export default RegisterProvider
