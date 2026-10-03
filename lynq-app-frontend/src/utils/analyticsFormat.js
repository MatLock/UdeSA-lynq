import strings, { activeLocale } from '../i18n'

const DAY_MONTH = { day: 'numeric', month: 'short', timeZone: 'UTC' }

const dateOf = (isoDate) => new Date(`${isoDate}T00:00:00Z`)

const todayIso = () => {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

const formatShortDate = (isoDate) =>
  new Intl.DateTimeFormat(activeLocale, DAY_MONTH).format(dateOf(isoDate))

const formatNumber = (value) =>
  new Intl.NumberFormat(activeLocale, { maximumFractionDigits: 1 }).format(value)

const formatMoney = (value, currency) =>
  new Intl.NumberFormat(activeLocale, {
    style: 'currency',
    currency,
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value)

const asOfLabel = (snapshotOn) =>
  snapshotOn === todayIso()
    ? strings.ds.numbers.asOfToday
    : strings.ds.numbers.asOf(formatShortDate(snapshotOn))

const categoryLabel = (category) => {
  const t = strings.pages.analytics.market
  if (!category) return t.uncategorized
  return t.categories[category] ?? category
}

const workTypeLabel = (workType) =>
  strings.pages.analytics.market.workType[workType] ?? workType

export default {
  formatShortDate,
  formatNumber,
  formatMoney,
  asOfLabel,
  categoryLabel,
  workTypeLabel,
}
