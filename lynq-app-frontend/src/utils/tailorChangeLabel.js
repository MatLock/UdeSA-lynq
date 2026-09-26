const SUMMARY = 'summary'
const SKILLS = 'skills'
const REORDER = 'reorder'

const named = (map, key, fallback) => map?.[key] ?? fallback

const joined = (values, map, separator) =>
  values.map((value) => named(map, value, value)).join(separator)

const tailorChangeLabel = (change, labels) => {
  const section = change?.section ?? ''
  const kind = change?.kind ?? ''
  const fields = Array.isArray(change?.fields) ? change.fields : []
  const fallback = change?.detail ?? ''

  if (!labels) return fallback

  if (kind === REORDER) {
    return labels.reorder.replace(
      '{section}',
      named(labels.sections, section, section),
    )
  }

  if (section === SUMMARY) return labels.summaryRewrite

  if (section === SKILLS) {
    if (fields.length === 0) return fallback
    return labels.skillsRewrite.replace(
      '{fields}',
      joined(fields, labels.skillBuckets, labels.separator),
    )
  }

  const entry = labels.entries?.[section]
  if (!entry || fields.length === 0 || !Number.isInteger(change?.index)) {
    return fallback
  }

  return labels.entryRewrite
    .replace('{entry}', entry)
    .replace('{position}', change.index + 1)
    .replace('{fields}', joined(fields, labels.entryFields, labels.separator))
}

export default tailorChangeLabel
