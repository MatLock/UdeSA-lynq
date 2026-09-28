import resumeSections from './resumeSections'
import { documentSectionOf } from './tailorChangedSections'

const { hasText, entriesOf } = resumeSections

const META_SEPARATOR = ' · '

const SUMMARY = 'summary'
const SKILLS = 'skills'
const REORDER = 'reorder'
const REWRITE = 'rewrite'
const REPLACE = 'replace'

const asText = (value) => (hasText(value) ? value.trim() : '')

const fill = (template, values) =>
  Object.entries(values).reduce(
    (text, [key, value]) => text.replace(`{${key}}`, value),
    template ?? '',
  )

const joinNames = (names, t) => {
  if (names.length <= 1) return names.join('')
  return `${names.slice(0, -1).join(t.separator)}${t.and}${names[names.length - 1]}`
}

const entryLabelOf = (entry) => {
  if (!entry) return ''
  const named = [entry.position, entry.company].filter(hasText).map(asText)
  if (named.length > 0) return named.join(META_SEPARATOR)
  return asText(entry.institution) || asText(entry.name) || asText(entry.degree)
}

const entryAt = (resume, section, index) =>
  entriesOf(resume?.[section])[index] ?? null

const firstEntryChanged = (before, after, section) => {
  const wasFirst = entryLabelOf(entryAt(before, section, 0))
  const isFirst = entryLabelOf(entryAt(after, section, 0))
  return isFirst && isFirst !== wasFirst ? isFirst : ''
}

const namesOf = (keys, dictionary) =>
  keys.map((key) => dictionary?.[key]).filter(hasText)

const sectionNameOf = (section, t) => t.sections?.[documentSectionOf(section)] ?? section

const rewriteLine = (change, turn, t) => {
  if (change.section === SUMMARY) return t.rewroteSummary

  const entry = entryLabelOf(entryAt(turn.after, change.section, change.index))
  const fields = joinNames(namesOf(change.fields ?? [], t.fields), t)
  if (!entry || !fields) return fill(t.changed, { section: sectionNameOf(change.section, t) })
  return fill(t.rewroteEntry, { fields, entry })
}

const reorderLine = (change, turn, t) => {
  const section = sectionNameOf(change.section, t)
  const first = firstEntryChanged(turn.before, turn.after, change.section)
  return first
    ? fill(t.reorderedFirst, { section, entry: first })
    : fill(t.reordered, { section })
}

const skillsLine = (change, t) => {
  const buckets = joinNames(namesOf(change.fields ?? [], t.buckets), t)
  return buckets
    ? fill(t.replacedSkills, { buckets })
    : fill(t.changed, { section: sectionNameOf(SKILLS, t) })
}

const lineOf = (change, turn, t) => {
  if (change?.kind === REWRITE) return rewriteLine(change, turn, t)
  if (change?.kind === REORDER) return reorderLine(change, turn, t)
  if (change?.kind === REPLACE && change.section === SKILLS) return skillsLine(change, t)
  return fill(t.changed, { section: sectionNameOf(change?.section, t) })
}

const linesOf = (turn, t) =>
  (turn?.changes ?? []).map((change) => lineOf(change, turn, t)).filter(hasText)

export default {
  lineOf,
  linesOf,
}
