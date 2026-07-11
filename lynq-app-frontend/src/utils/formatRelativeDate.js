// Relative-time formatting for job posting dates (e.g. "hace 2 días",
// "2 days ago"). Locale-aware via Intl.RelativeTimeFormat, driven by the app's
// active i18n locale so it matches the rest of the UI.
import { activeLocale } from '../i18n'

// Units from largest to smallest, each with its length in seconds. We render
// with the largest unit whose span the elapsed time reaches, so "8 days ago"
// collapses to "1 week ago" — matching the design's coarse phrasing.
const UNITS = [
  ['year', 60 * 60 * 24 * 365],
  ['month', 60 * 60 * 24 * 30],
  ['week', 60 * 60 * 24 * 7],
  ['day', 60 * 60 * 24],
  ['hour', 60 * 60],
  ['minute', 60],
]

// Format an ISO date string as a past-relative phrase. Returns '' for a missing
// or unparseable date so callers can simply omit the meta line.
const formatRelativeDate = (dateString, locale = activeLocale) => {
  if (!dateString) return ''

  const then = new Date(dateString).getTime()
  if (Number.isNaN(then)) return ''

  const elapsedSeconds = Math.max(0, (Date.now() - then) / 1000)
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })

  for (const [unit, seconds] of UNITS) {
    if (elapsedSeconds >= seconds) {
      return rtf.format(-Math.floor(elapsedSeconds / seconds), unit)
    }
  }

  // Under a minute old — treat as just now.
  return rtf.format(0, 'second')
}

export default formatRelativeDate
