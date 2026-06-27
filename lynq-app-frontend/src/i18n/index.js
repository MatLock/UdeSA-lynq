import es from './locales/es'
import en from './locales/en'

const locales = { es, en }
const DEFAULT_LOCALE = 'es'

// Active translation dictionary. Change DEFAULT_LOCALE (or swap this for a
// context/state-driven selector) to switch languages app-wide.
const strings = locales[DEFAULT_LOCALE]

export default strings
export { locales, DEFAULT_LOCALE }
