import './ResumeOptionCards.css'

// The radio-card pair a resume-wizard step uses to ask an either/or question —
// "form or upload" in step 1, "classic or modern" in the last step. Extracted so
// both look identical by construction instead of by copied CSS.
//
// Controlled via `value` + `onChange` (the selected option's value). Each option
// is { value, tone, title, description, Icon }, where `tone` picks the icon's
// brand tint (blue or purple).
const ResumeOptionCards = ({ name, value, options, onChange }) => (
  <div className="resume-options">
    {options.map(({ value: optionValue, tone, title, description, Icon }) => {
      const selected = value === optionValue
      return (
        <label
          key={optionValue}
          className={
            selected
              ? `resume-option-card tone-${tone} selected`
              : `resume-option-card tone-${tone}`
          }
        >
          <input
            type="radio"
            name={name}
            value={optionValue}
            checked={selected}
            onChange={(event) => onChange(event.target.value)}
          />
          <span className="resume-option-radio" aria-hidden="true" />
          <span className="resume-option-icon">
            <Icon sx={{ fontSize: 30 }} />
          </span>
          <span className="resume-option-title">{title}</span>
          <span className="resume-option-desc">{description}</span>
        </label>
      )
    })}
  </div>
)

export default ResumeOptionCards
