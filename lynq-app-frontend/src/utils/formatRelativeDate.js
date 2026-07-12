// Relative-time formatting for job posting dates (e.g. "hace 2 días",
// "2 days ago"). Locale-aware via Intl.RelativeTimeFormat, driven by the app's
// active i18n locale so it matches the rest of the UI.
//
// Backend posting dates are date-only ("YYYY-MM-DD", a java.time.LocalDate), so
// there is no time-of-day component. We therefore compare at calendar-day
// granularity in the viewer's local timezone. This matters for fresh posts: a
// job created earlier today must read "today" — not "14 hours ago", which is
// what anchoring a bare date to UTC midnight and measuring in hours would
// wrongly produce (visible on LYNQ posts, where the date is recent).
import { activeLocale } from '../i18n'

const DAY_MS = 24 * 60 * 60 * 1000

// Coarser-than-day units, largest first, each measured in whole days. Anything
// under a week is expressed directly in days so "today"/"yesterday" show.
const UNITS = [
  ['year', 365],
  ['month', 30],
  ['week', 7],
]

// Parse a date-only string into a LOCAL calendar date at midnight (so the day is
// not shifted by the UTC offset). Returns null for a missing / unparseable date.
const parseLocalDate = (dateString) => {
  if (!dateString) return null

  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(dateString)
  if (match) {
    const [, year, month, day] = match
    return new Date(Number(year), Number(month) - 1, Number(day))
  }

  const fallback = new Date(dateString)
  return Number.isNaN(fallback.getTime()) ? null : fallback
}

const startOfDay = (date) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate())

// Format a date-only string as a past-relative phrase. Returns '' for a missing
// or unparseable date so callers can simply omit the meta line.
const formatRelativeDate = (dateString, locale = activeLocale) => {
  const then = parseLocalDate(dateString)
  if (!then) return ''

  // Whole calendar days between the post date and today, both at local midnight;
  // rounding absorbs any DST hour drift. Clamped at 0 so a same-day (or clock-
  // skewed future) date never reads as negative.
  const elapsedDays = Math.max(
    0,
    Math.round((startOfDay(new Date()) - startOfDay(then)) / DAY_MS),
  )
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })

  for (const [unit, days] of UNITS) {
    if (elapsedDays >= days) {
      return rtf.format(-Math.floor(elapsedDays / days), unit)
    }
  }

  // Under a week old — express in days so numeric:'auto' yields "today" (0) and
  // "yesterday" (1) instead of raw counts.
  return rtf.format(-elapsedDays, 'day')
}

export default formatRelativeDate
