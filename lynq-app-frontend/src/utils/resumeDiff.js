import resumeSections from './resumeSections'

const { hasText, entriesOf } = resumeSections

const LINK_KEYS = ['linkedin', 'github', 'portfolio', 'website']
const SKILL_KEYS = ['technical', 'tools', 'soft']

const SAME = 'same'
const ADDED = 'added'
const REMOVED = 'removed'

const FIELD_SEPARATOR = '::|::'
const META_SEPARATOR = ' · '
const DATE_SEPARATOR = ' – '
const WORD_SEPARATOR = /(\s+)/
const MIN_REWRITE_SIMILARITY = 0.4

const asText = (value) => (hasText(value) ? value.trim() : '')

const joinMeta = (parts) => parts.filter(hasText).map(asText).join(META_SEPARATOR)

const paragraphsOf = (value) =>
  asText(value)
    .split('\n')
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)

const dateRangeOf = (entry, presentLabel) => {
  const end = entry?.is_current ? presentLabel : asText(entry?.end_date)
  return [asText(entry?.start_date), end].filter(Boolean).join(DATE_SEPARATOR)
}

const flattenResume = (resume, labels) => {
  if (!resume) return []

  const rows = []
  const add = (section, entry, field, value) => {
    paragraphsOf(value).forEach((text) => rows.push({ section, entry, field, text }))
  }
  const addEach = (section, entry, field, values) => {
    entriesOf(values).forEach((value) => add(section, entry, field, value))
  }

  const personalInfo = resume.personal_info ?? {}
  const links = personalInfo.links ?? {}
  add('personal', '', labels.fullName, personalInfo.full_name)
  add('personal', '', labels.headline, personalInfo.headline)
  add('personal', '', labels.email, personalInfo.email)
  add('personal', '', labels.phone, personalInfo.phone)
  add('personal', '', labels.location, personalInfo.location)
  LINK_KEYS.forEach((key) => add('personal', '', labels[key], links[key]))

  add('summary', '', labels.summary, resume.summary)

  entriesOf(resume.work_experience).forEach((job) => {
    const entry = joinMeta([job.position, job.company])
    add('experience', entry, labels.dates, dateRangeOf(job, labels.current))
    add('experience', entry, labels.location, job.location)
    add('experience', entry, labels.description, job.description)
    addEach('experience', entry, labels.achievements, job.achievements)
    addEach('experience', entry, labels.technologies, job.technologies)
  })

  entriesOf(resume.education).forEach((study) => {
    const entry = asText(study.institution)
    add('education', entry, labels.degree, joinMeta([study.degree, study.field_of_study]))
    add('education', entry, labels.dates, dateRangeOf(study, labels.current))
    add('education', entry, labels.description, study.description)
  })

  SKILL_KEYS.forEach((key) => addEach('skills', '', labels[key], resume.skills?.[key]))

  entriesOf(resume.languages).forEach((language) => {
    add('languages', asText(language.language), labels.proficiency, language.proficiency)
  })

  entriesOf(resume.certifications).forEach((certification) => {
    const entry = asText(certification.name)
    add('certifications', entry, labels.issuedBy, certification.issuer)
    add('certifications', entry, labels.issueDate, certification.issue_date)
    add('certifications', entry, labels.credentialId, certification.credential_id)
  })

  entriesOf(resume.projects).forEach((project) => {
    const entry = asText(project.name)
    add('projects', entry, labels.description, project.description)
    addEach('projects', entry, labels.technologies, project.technologies)
    add('projects', entry, labels.url, project.url)
  })

  return rows
}

const identityOf = (row) =>
  [row.section, row.entry, row.field, row.text].join(FIELD_SEPARATOR)

const commonSuffixLengths = (before, after) => {
  const lengths = Array.from({ length: before.length + 1 }, () =>
    new Array(after.length + 1).fill(0),
  )
  for (let left = before.length - 1; left >= 0; left -= 1) {
    for (let right = after.length - 1; right >= 0; right -= 1) {
      lengths[left][right] =
        before[left] === after[right]
          ? lengths[left + 1][right + 1] + 1
          : Math.max(lengths[left + 1][right], lengths[left][right + 1])
    }
  }
  return lengths
}

const alignSequences = (before, after) => {
  const lengths = commonSuffixLengths(before, after)
  const steps = []
  let left = 0
  let right = 0

  while (left < before.length && right < after.length) {
    if (before[left] === after[right]) {
      steps.push({ type: SAME, leftIndex: left, rightIndex: right })
      left += 1
      right += 1
    } else if (lengths[left + 1][right] >= lengths[left][right + 1]) {
      steps.push({ type: REMOVED, leftIndex: left })
      left += 1
    } else {
      steps.push({ type: ADDED, rightIndex: right })
      right += 1
    }
  }
  while (left < before.length) {
    steps.push({ type: REMOVED, leftIndex: left })
    left += 1
  }
  while (right < after.length) {
    steps.push({ type: ADDED, rightIndex: right })
    right += 1
  }
  return steps
}

const wordsOf = (text) => text.split(WORD_SEPARATOR).filter((word) => word.length > 0)

const mergeParts = (parts) =>
  parts.reduce((merged, part) => {
    const previous = merged[merged.length - 1]
    if (previous && previous.changed === part.changed) {
      previous.text += part.text
      return merged
    }
    return [...merged, { ...part }]
  }, [])

const plainParts = (text) => [{ text, changed: false }]

const isBlank = (word) => word.trim().length === 0

const countWords = (words) => words.filter((word) => !isBlank(word)).length

const similarityOf = (steps, removedWords, addedWords) => {
  const longest = Math.max(countWords(removedWords), countWords(addedWords))
  if (longest === 0) return 0
  const kept = steps.filter(
    (step) => step.type === SAME && !isBlank(removedWords[step.leftIndex]),
  ).length
  return kept / longest
}

const highlightWords = (removedText, addedText) => {
  const removedWords = wordsOf(removedText)
  const addedWords = wordsOf(addedText)
  const steps = alignSequences(removedWords, addedWords)

  if (similarityOf(steps, removedWords, addedWords) < MIN_REWRITE_SIMILARITY) {
    return {
      removedParts: plainParts(removedText),
      addedParts: plainParts(addedText),
    }
  }

  const removedParts = []
  const addedParts = []
  steps.forEach((step) => {
    if (step.type === SAME) {
      removedParts.push({ text: removedWords[step.leftIndex], changed: false })
      addedParts.push({ text: addedWords[step.rightIndex], changed: false })
    } else if (step.type === REMOVED) {
      removedParts.push({ text: removedWords[step.leftIndex], changed: true })
    } else {
      addedParts.push({ text: addedWords[step.rightIndex], changed: true })
    }
  })

  return { removedParts: mergeParts(removedParts), addedParts: mergeParts(addedParts) }
}

const isRewriteOf = (removed, added) =>
  removed.section === added.section &&
  removed.entry === added.entry &&
  removed.field === added.field

const withWordHighlights = (lines) => {
  const highlighted = lines.map((line) => ({ ...line, parts: plainParts(line.text) }))

  highlighted.forEach((line, index) => {
    const next = highlighted[index + 1]
    if (line.type !== REMOVED || next?.type !== ADDED) return
    if (!isRewriteOf(line, next)) return
    const { removedParts, addedParts } = highlightWords(line.text, next.text)
    line.parts = removedParts
    next.parts = addedParts
  })

  return highlighted
}

const groupBySection = (lines) =>
  lines.reduce((groups, line) => {
    const current = groups[groups.length - 1]
    if (current?.section === line.section && current?.entry === line.entry) {
      current.lines.push(line)
      return groups
    }
    return [...groups, { section: line.section, entry: line.entry, lines: [line] }]
  }, [])

const compare = (before, after, labels) => {
  const beforeRows = flattenResume(before, labels)
  const afterRows = flattenResume(after, labels)
  const beforeIds = beforeRows.map(identityOf)
  const afterIds = afterRows.map(identityOf)

  const changedLines = alignSequences(beforeIds, afterIds)
    .filter((step) => step.type !== SAME)
    .map((step) =>
      step.type === REMOVED
        ? { ...beforeRows[step.leftIndex], type: REMOVED }
        : { ...afterRows[step.rightIndex], type: ADDED },
    )

  const lines = withWordHighlights(changedLines)

  return {
    groups: groupBySection(lines),
    added: lines.filter((line) => line.type === ADDED).length,
    removed: lines.filter((line) => line.type === REMOVED).length,
  }
}

export default {
  compare,
  flattenResume,
  ADDED,
  REMOVED,
}
