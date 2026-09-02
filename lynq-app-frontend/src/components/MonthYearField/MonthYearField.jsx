import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined'
import strings, { activeLocale } from '../../i18n'
import './MonthYearField.css'

// How far back the year list goes. 60 years covers any working history a resume
// would state, and the list is generated from the current year at render time.
const YEARS_BACK = 60

// Localized month names ("ene", "feb", … / "Jan", "Feb", …) for the active UI
// locale, indexed 0-11 to match the select values.
const MONTHS = (() => {
  const format = new Intl.DateTimeFormat(activeLocale, { month: 'short' })
  return Array.from({ length: 12 }, (_, index) =>
    format.format(new Date(2024, index, 1)),
  )
})()

const pad = (value) => String(value).padStart(2, '0')

// Editor for the partial dates a resume carries: "YYYY-MM" when the month is
// known, "YYYY" when only the year is, and '' when the field is left empty. A
// month alone is not a valid value, so picking one without a year keeps the field
// empty until the year is chosen.
//
// Two selects (rather than a calendar) match the data exactly: resume dates have
// no day component, so offering one would invite information the payload cannot
// carry.
const MonthYearField = ({ id, value, onChange, disabled = false }) => {
  const t = strings.pages.resume.create.dates

  const [year = '', month = ''] = (value ?? '').split('-')
  const currentYear = new Date().getFullYear()
  const years = Array.from({ length: YEARS_BACK }, (_, index) => currentYear - index)

  const emit = (nextYear, nextMonth) => {
    if (!nextYear) {
      onChange('')
      return
    }
    onChange(nextMonth ? `${nextYear}-${nextMonth}` : nextYear)
  }

  return (
    <div className="month-year-field">
      <select
        id={id}
        className="month-year-select"
        aria-label={t.year}
        value={year}
        disabled={disabled}
        onChange={(event) => emit(event.target.value, month)}
      >
        <option value="">{t.noYear}</option>
        {years.map((option) => (
          <option key={option} value={String(option)}>
            {option}
          </option>
        ))}
      </select>

      <select
        className="month-year-select"
        aria-label={t.month}
        value={month}
        disabled={disabled || !year}
        onChange={(event) => emit(year, event.target.value)}
      >
        <option value="">{t.noMonth}</option>
        {MONTHS.map((label, index) => (
          <option key={label} value={pad(index + 1)}>
            {label}
          </option>
        ))}
      </select>

      {value && !disabled && (
        <button
          type="button"
          className="month-year-clear"
          aria-label={strings.pages.resume.create.remove}
          onClick={() => onChange('')}
        >
          <CloseOutlinedIcon sx={{ fontSize: 14 }} />
        </button>
      )}
    </div>
  )
}

export default MonthYearField
