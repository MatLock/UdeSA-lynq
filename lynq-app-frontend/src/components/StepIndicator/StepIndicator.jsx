import strings from '../../i18n'
import './StepIndicator.css'

// Shows how far through a wizard the user is, e.g. "Paso 1 de 2". The total is
// passed in by the wizard, which owns the step list: for register it varies by
// account type (candidate = 2, company = 3), for the resume flow by the chosen
// creation method. `template` lets a flow supply its own counter string; it
// defaults to the register wording, which every wizard shares.
const StepIndicator = ({
  current,
  total,
  className = '',
  template = strings.register.stepCounter,
}) => {
  const text = template
    .replace('{current}', current)
    .replace('{total}', total)

  return (
    <p className={`step-indicator ${className}`.trim()} aria-live="polite">
      {text}
    </p>
  )
}

export default StepIndicator
