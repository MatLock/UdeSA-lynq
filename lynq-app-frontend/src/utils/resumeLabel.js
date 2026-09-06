// What a stored resume is called wherever the candidate has to tell theirs
// apart: the alias they assigned wins, falling back to the name the document
// itself carries (for an imported PDF, its file name; for one built in the
// wizard, the candidate's full name).
//
// Shared rather than restated per page so the switcher on "My resume" and the
// picker in the apply dialog never disagree about what a resume is called —
// picking the wrong one there means applying with the wrong CV.

// `fallback` is the caller's already-translated "untitled" label, since a resume
// can have neither an alias nor a name.
const resumeLabel = (resume, fallback = '') =>
  resume?.alias || resume?.name || fallback

export default resumeLabel
