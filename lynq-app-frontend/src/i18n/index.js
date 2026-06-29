import es from './locales/es'
import en from './locales/en'

const locales = { es, en }
const DEFAULT_LOCALE = 'es'
const STORAGE_KEY = 'lynq.locale'

// Persisted locale, falling back to the default. Read once at module load so the
// exported `strings` dictionary is resolved synchronously for every consumer.
const readLocale = () => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored && locales[stored] ? stored : DEFAULT_LOCALE
  } catch {
    return DEFAULT_LOCALE
  }
}

const activeLocale = readLocale()

// Active translation dictionary for the current load. Switching language is done
// via setLocale(), which persists the choice and reloads so every statically
// imported `strings` reference re-resolves against the new locale.
const strings = locales[activeLocale]

const setLocale = (locale) => {
  if (!locales[locale] || locale === activeLocale) return
  try {
    localStorage.setItem(STORAGE_KEY, locale)
  } catch {
    // Storage unavailable (private mode) — nothing to persist; bail out.
    return
  }
  window.location.reload()
}

export default strings
export { locales, DEFAULT_LOCALE, activeLocale, setLocale }
