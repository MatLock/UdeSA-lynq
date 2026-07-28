import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined'
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded'
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded'
import strings, { DEFAULT_LOCALE } from '../../i18n'
import './DatePicker.css'

const pad = (n) => String(n).padStart(2, '0')
const toISO = (year, month, day) => `${year}-${pad(month + 1)}-${pad(day)}`
const ISO_RE = /^\d{4}-\d{2}-\d{2}$/

// Approx popup height, used only to decide whether to flip it above the field.
const POPUP_HEIGHT = 280
const POPUP_WIDTH = 240

// Parse a strict yyyy-mm-dd string into a Date, rejecting impossible dates
// (e.g. 2025-02-30). Returns null when the string isn't a complete valid date.
const parseISO = (value) => {
  if (!ISO_RE.test(value)) return null
  const [y, m, d] = value.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  if (date.getFullYear() !== y || date.getMonth() !== m - 1 || date.getDate() !== d) {
    return null
  }
  return date
}

const startOfDay = (date) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate())

// Monday-first weekday labels for the configured locale (2024-01-01 is a Monday).
const WEEKDAYS = (() => {
  const fmt = new Intl.DateTimeFormat(DEFAULT_LOCALE, { weekday: 'short' })
  return Array.from({ length: 7 }, (_, i) => fmt.format(new Date(2024, 0, 1 + i)))
})()

// Self-contained date field: the user can type yyyy-mm-dd OR pick from the popup
// calendar. Controlled via `value`/`onChange` (both as yyyy-mm-dd strings), so it
// stays a drop-in replacement for a plain text input. The popup is portaled to
// <body> so it isn't clipped by ancestors with overflow:hidden (e.g. the wizard).
const DatePicker = ({ id, value, onChange, placeholder, ariaInvalid, disableFuture }) => {
  const t = strings.datePicker
  const [open, setOpen] = useState(false)
  const [view, setView] = useState(() => parseISO(value) || new Date())
  const [coords, setCoords] = useState(null)
  const fieldRef = useRef(null)
  const popupRef = useRef(null)

  const selected = parseISO(value)

  // Open the popup, snapping the displayed month to the current (typed) value.
  const toggleOpen = () => {
    setOpen((prev) => {
      if (!prev) setView(parseISO(value) || new Date())
      return !prev
    })
  }

  // Position the popup under (or above) the field, in viewport coordinates.
  const place = () => {
    const rect = fieldRef.current?.getBoundingClientRect()
    if (!rect) return
    const flipUp = rect.bottom + 6 + POPUP_HEIGHT > window.innerHeight
    setCoords({
      top: flipUp ? rect.top - 6 - POPUP_HEIGHT : rect.bottom + 6,
      left: Math.max(8, Math.min(rect.left, window.innerWidth - POPUP_WIDTH - 8)),
    })
  }

  // Reposition while open, and close on outside click / Escape.
  useEffect(() => {
    if (!open) return
    place()
    const onReflow = () => place()
    const onPointerDown = (event) => {
      const inField = fieldRef.current?.contains(event.target)
      const inPopup = popupRef.current?.contains(event.target)
      if (!inField && !inPopup) setOpen(false)
    }
    const onKeyDown = (event) => {
      if (event.key === 'Escape') setOpen(false)
    }
    window.addEventListener('resize', onReflow)
    window.addEventListener('scroll', onReflow, true)
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      window.removeEventListener('resize', onReflow)
      window.removeEventListener('scroll', onReflow, true)
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const year = view.getFullYear()
  const month = view.getMonth()
  const monthLabel = new Intl.DateTimeFormat(DEFAULT_LOCALE, {
    month: 'long',
    year: 'numeric',
  }).format(view)

  // Day grid, Monday-first, with leading blanks for alignment.
  const startOffset = (new Date(year, month, 1).getDay() + 6) % 7
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells = []
  for (let i = 0; i < startOffset; i += 1) cells.push({ key: `blank-${i}`, day: null })
  for (let d = 1; d <= daysInMonth; d += 1) cells.push({ key: `day-${d}`, day: d })

  const maxDay = disableFuture ? startOfDay(new Date()) : null
  const isDisabled = (day) => maxDay && new Date(year, month, day) > maxDay

  const isSelected = (day) =>
    selected &&
    selected.getFullYear() === year &&
    selected.getMonth() === month &&
    selected.getDate() === day

  const goMonth = (delta) => setView(new Date(year, month + delta, 1))

  const selectDay = (day) => {
    onChange(toISO(year, month, day))
    setOpen(false)
  }

  return (
    <div className="date-picker">
      <div className="date-picker-field" ref={fieldRef}>
        <input
          id={id}
          type="text"
          inputMode="numeric"
          maxLength={10}
          placeholder={placeholder}
          value={value}
          aria-invalid={ariaInvalid}
          onChange={(event) => onChange(event.target.value)}
        />
        <button
          type="button"
          className="date-picker-toggle"
          aria-label={t.open}
          aria-expanded={open}
          onClick={toggleOpen}
        >
          <CalendarMonthOutlinedIcon sx={{ fontSize: 18 }} />
        </button>
      </div>

      {open && coords &&
        createPortal(
          <dialog
            ref={popupRef}
            open
            className="date-picker-popup"
            aria-label={t.open}
            style={{ top: coords.top, left: coords.left, width: POPUP_WIDTH }}
          >
            <div className="date-picker-head">
              <button
                type="button"
                className="date-picker-nav"
                aria-label={t.previousMonth}
                onClick={() => goMonth(-1)}
              >
                <ChevronLeftRoundedIcon sx={{ fontSize: 20 }} />
              </button>
              <span className="date-picker-month">{monthLabel}</span>
              <button
                type="button"
                className="date-picker-nav"
                aria-label={t.nextMonth}
                onClick={() => goMonth(1)}
              >
                <ChevronRightRoundedIcon sx={{ fontSize: 20 }} />
              </button>
            </div>

            <div className="date-picker-grid date-picker-weekdays">
              {WEEKDAYS.map((label) => (
                <span key={label} className="date-picker-weekday">{label}</span>
              ))}
            </div>

            <div className="date-picker-grid">
              {cells.map(({ key, day }) =>
                day === null ? (
                  <span key={key} className="date-picker-cell empty" />
                ) : (
                  <button
                    key={key}
                    type="button"
                    className={isSelected(day) ? 'date-picker-cell selected' : 'date-picker-cell'}
                    disabled={isDisabled(day)}
                    onClick={() => selectDay(day)}
                  >
                    {day}
                  </button>
                ),
              )}
            </div>
          </dialog>,
          document.body,
        )}
    </div>
  )
}

export default DatePicker
