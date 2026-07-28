// Which sections of a resume JSON actually have something to show.
//
// A resume comes from an LLM extraction, so every field is optional and lists
// arrive empty as often as populated. The viewer's left-hand navigation and the
// document body must agree on exactly which sections exist, so both read the
// list from here instead of each re-deriving it.
//
// Section ids match the keys under `pages.resume.sections` in the i18n
// dictionaries, so a caller turns an id into a label with a plain lookup.

const hasText = (value) => typeof value === 'string' && value.trim().length > 0

// Non-empty entries of a resume list field, tolerating a null/absent list.
const entriesOf = (value) => (Array.isArray(value) ? value.filter(Boolean) : [])

// True when the personal-info block carries anything worth rendering.
const hasPersonalInfo = (personalInfo) => {
  if (!personalInfo) return false
  const { links, ...fields } = personalInfo
  return (
    Object.values(fields).some(hasText) ||
    Object.values(links ?? {}).some(hasText)
  )
}

// Total skills across the three buckets — the section's badge count.
const countSkills = (skills) =>
  entriesOf(skills?.technical).length +
  entriesOf(skills?.tools).length +
  entriesOf(skills?.soft).length

// The ordered, non-empty sections of a resume as { id, count } — `count` is the
// number of entries for list-shaped sections and null for prose ones (personal
// info, summary), which have nothing meaningful to count.
const sectionsOf = (resume) => {
  if (!resume) return []

  const candidates = [
    { id: 'personal', count: null, present: hasPersonalInfo(resume.personal_info) },
    { id: 'summary', count: null, present: hasText(resume.summary) },
    { id: 'experience', count: entriesOf(resume.work_experience).length },
    { id: 'education', count: entriesOf(resume.education).length },
    { id: 'skills', count: countSkills(resume.skills) },
    { id: 'languages', count: entriesOf(resume.languages).length },
    { id: 'certifications', count: entriesOf(resume.certifications).length },
    { id: 'projects', count: entriesOf(resume.projects).length },
  ]

  return candidates
    .filter(({ count, present }) => (present === undefined ? count > 0 : present))
    .map(({ id, count }) => ({ id, count }))
}

export default {
  hasText,
  entriesOf,
  sectionsOf,
}
