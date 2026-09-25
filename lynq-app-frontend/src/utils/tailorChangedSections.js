const DOCUMENT_SECTION_BY_RESUME_FIELD = {
  personal_info: 'personal',
  summary: 'summary',
  skills: 'skills',
  work_experience: 'experience',
  education: 'education',
  projects: 'projects',
  languages: 'languages',
  certifications: 'certifications',
}

const documentSectionOf = (changedSection) => {
  const resumeField = String(changedSection ?? '').split('[')[0].trim()
  return DOCUMENT_SECTION_BY_RESUME_FIELD[resumeField] ?? ''
}

const tailorChangedSections = (changes) => [
  ...new Set(
    (changes ?? [])
      .map((change) => documentSectionOf(change?.section))
      .filter(Boolean),
  ),
]

export default tailorChangedSections
