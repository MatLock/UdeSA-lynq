// Turning the resume wizard's form state into the resume JSON the backend stores.
//
// The two shapes differ in ways worth keeping in one place: form inputs are always
// strings (an untouched field is '', never null), whereas the stored resume uses
// null for "unknown" — and the wizard lets the user append entries they may then
// leave blank, which must not reach the payload as empty rows.

const blank = (value) => typeof value === 'string' && value.trim().length === 0

// '' → null, otherwise the trimmed text. Non-strings (booleans, arrays) pass
// through untouched.
const emptyToNull = (value) => {
  if (typeof value !== 'string') return value
  const trimmed = value.trim()
  return trimmed.length === 0 ? null : trimmed
}

// True when the user added an entry and typed nothing into it: every string field
// is blank/absent and every list is empty. `is_current` is ignored on purpose — a
// stray checkbox tick is not content.
const isBlankEntry = (entry) =>
  Object.entries(entry).every(([key, value]) => {
    if (key === 'is_current') return true
    if (Array.isArray(value)) return value.length === 0
    return value === null || value === undefined || blank(value)
  })

// Drop the entries the user never filled in.
const pruneEntries = (entries) => entries.filter((entry) => !isBlankEntry(entry))

// One-line recap of an entry for the header of a collapsed card — the couple of
// fields that identify it ("Backend Engineer · Acme"), blanks dropped so a
// half-filled entry still reads cleanly.
const summarize = (...parts) =>
  parts
    .map((part) => (typeof part === 'string' ? part.trim() : ''))
    .filter(Boolean)
    .join(' · ')

// Trimmed, de-duplicated tag list (skills, technologies, achievements).
const cleanList = (items) => {
  const seen = new Set()
  const out = []
  for (const item of items ?? []) {
    const entry = typeof item === 'string' ? item.trim() : ''
    if (entry && !seen.has(entry.toLowerCase())) {
      seen.add(entry.toLowerCase())
      out.push(entry)
    }
  }
  return out
}

// Assemble the wizard's collected state into the resume JSON. Every key of the
// stored shape is emitted (with null / [] where the user gave nothing), so the
// payload is complete regardless of how much of the wizard was filled in.
const toResumePayload = (draft) => {
  const personal = draft.personal ?? {}

  return {
    personal_info: {
      full_name: emptyToNull(personal.full_name ?? ''),
      headline: emptyToNull(personal.headline ?? ''),
      email: emptyToNull(personal.email ?? ''),
      phone: emptyToNull(personal.phone ?? ''),
      location: emptyToNull(personal.location ?? ''),
      links: {
        linkedin: emptyToNull(personal.linkedin ?? ''),
        github: emptyToNull(personal.github ?? ''),
        portfolio: emptyToNull(personal.portfolio ?? ''),
        website: emptyToNull(personal.website ?? ''),
      },
    },
    summary: emptyToNull(personal.summary ?? ''),
    work_experience: pruneEntries(draft.experience ?? []).map((job) => ({
      company: emptyToNull(job.company) ?? '',
      position: emptyToNull(job.position) ?? '',
      location: emptyToNull(job.location),
      start_date: emptyToNull(job.start_date),
      // A current job has no end date, whatever was picked before the tick.
      end_date: job.is_current ? null : emptyToNull(job.end_date),
      is_current: Boolean(job.is_current),
      description: emptyToNull(job.description),
      achievements: cleanList(job.achievements),
      technologies: cleanList(job.technologies),
    })),
    education: pruneEntries(draft.education ?? []).map((study) => ({
      institution: emptyToNull(study.institution) ?? '',
      degree: emptyToNull(study.degree),
      field_of_study: emptyToNull(study.field_of_study),
      start_date: emptyToNull(study.start_date),
      end_date: study.is_current ? null : emptyToNull(study.end_date),
      is_current: Boolean(study.is_current),
      description: emptyToNull(study.description),
    })),
    skills: {
      technical: cleanList(draft.skills?.technical),
      tools: cleanList(draft.skills?.tools),
      soft: cleanList(draft.skills?.soft),
    },
    languages: pruneEntries(draft.languages ?? []).map((language) => ({
      language: emptyToNull(language.language) ?? '',
      proficiency: emptyToNull(language.proficiency),
    })),
    certifications: pruneEntries(draft.certifications ?? []).map((certification) => ({
      name: emptyToNull(certification.name) ?? '',
      issuer: emptyToNull(certification.issuer),
      issue_date: emptyToNull(certification.issue_date),
      credential_id: emptyToNull(certification.credential_id),
    })),
    projects: pruneEntries(draft.projects ?? []).map((project) => ({
      name: emptyToNull(project.name) ?? '',
      description: emptyToNull(project.description),
      technologies: cleanList(project.technologies),
      url: emptyToNull(project.url),
    })),
  }
}

export default {
  isBlankEntry,
  pruneEntries,
  cleanList,
  summarize,
  toResumePayload,
}
