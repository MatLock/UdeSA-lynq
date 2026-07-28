import { useEffect, useState } from 'react'

// How long each phrase stays on screen. Long enough to read without feeling
// static, short enough that a slow request still looks like it's making progress.
const DEFAULT_INTERVAL_MS = 2600

// Cycles through a list of phrases while `active`, for waits long enough that a
// single fixed caption reads as a stall (e.g. an LLM call). Returns the phrase to
// show right now; the caller passes it straight to a Spinner/LoadingOverlay label.
//
// The phrases come from the i18n dictionary, so they are already in the
// configured UI language.
//
// The counter is not reset between runs on purpose: a second run picks up where
// the last left off, so repeating an action doesn't replay the same opening line.
const useRotatingPhrase = (phrases, active = true, intervalMs = DEFAULT_INTERVAL_MS) => {
  const [tick, setTick] = useState(0)

  useEffect(() => {
    if (!active || phrases.length <= 1) return
    const timer = setInterval(() => setTick((previous) => previous + 1), intervalMs)
    return () => clearInterval(timer)
  }, [active, intervalMs, phrases.length])

  if (phrases.length === 0) return ''
  return phrases[tick % phrases.length]
}

export default useRotatingPhrase
