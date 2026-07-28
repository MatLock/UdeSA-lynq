import './ResumeField.css'

// One labelled control inside a resume-wizard step: label on top, the control
// itself (passed as children, so the same wrapper serves inputs, textareas,
// selects and the custom date/tag widgets), then an optional hint and error.
//
// It owns the input styling for the whole resume flow — every step composes this
// instead of restating field CSS — mirroring how the auth pages share the
// login-form field look.
const ResumeField = ({ id, label, error, hint, full = false, children }) => (
  <div className={full ? 'resume-field resume-field--full' : 'resume-field'}>
    {label && <label htmlFor={id}>{label}</label>}
    {children}
    {hint && <p className="resume-field-hint">{hint}</p>}
    {error && (
      <p className="resume-field-error" role="alert">
        {error}
      </p>
    )}
  </div>
)

export default ResumeField
