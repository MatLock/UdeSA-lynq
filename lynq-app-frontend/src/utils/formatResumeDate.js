// Formatting for the partial dates carried by a resume JSON. Unlike the rest of
// the app (which deals in full "YYYY-MM-DD" LocalDates), resume dates are
// deliberately coarse: "YYYY-MM" when the month is known, "YYYY" when only the
// year is, and null when the field was left empty. Month names come from Intl,
// driven by the app's active i18n locale so they match the rest of the UI.
import { activeLocale } from '../i18n'

const YEAR_MONTH_RE = /^(\d{4})-(\d{2})$/
const YEAR_RE = /^(\d{4})$/

// "Mar 2024" for a year-month, "2024" for a bare year. Anything else (including
// null) yields '' so callers can simply omit the line.
const formatResumeDate = (value, locale = activeLocale) => {
  if (!value) return ''

  const yearMonth = YEAR_MONTH_RE.exec(value)
  if (yearMonth) {
    const [, year, month] = yearMonth
    const label = new Intl.DateTimeFormat(locale, {
      month: 'short',
      year: 'numeric',
    }).format(new Date(Number(year), Number(month) - 1, 1))
    return label
  }

  const year = YEAR_RE.exec(value)
  if (year) return year[1]

  // Unexpected shape (e.g. a full date slipped through) — show it verbatim
  // rather than dropping information the user typed.
  return value
}

// "Mar 2024 — Present" / "2020 — 2024" / "Mar 2024" for an open-ended range.
// `presentLabel` is supplied by the caller so the wording stays translated.
// Returns '' when neither end of the range is known.
const formatResumeDateRange = (start, end, isCurrent, presentLabel, locale = activeLocale) => {
  const from = formatResumeDate(start, locale)
  const to = isCurrent ? presentLabel : formatResumeDate(end, locale)

  if (from && to) return `${from} — ${to}`
  return from || to || ''
}

export default {
  formatResumeDate,
  formatResumeDateRange,
}
