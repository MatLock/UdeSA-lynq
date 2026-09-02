import { useState } from 'react'
import resumeDraft from '../utils/resumeDraft'

const { isBlankEntry } = resumeDraft

// No card is open. Also what `expanded` becomes when the open card is removed or
// toggled shut.
const COLLAPSED = -1

// Which card a list opens on: the first entry still to be filled in. A fresh step
// therefore opens its one blank card, while a list restored from the draft opens
// fully collapsed — everything in it is already written and needs no attention.
// "No blank entry" is findIndex's own -1, which is exactly COLLAPSED.
const initialExpanded = (entries) => entries.findIndex(isBlankEntry)

// One repeatable list of the resume wizard — jobs, studies, projects, languages,
// certifications — with an accordion on top of it: at most one entry is expanded
// at a time, and adding an entry opens the new one and collapses the rest.
//
// The wizard appends new entries at the bottom, so without this a candidate with
// five jobs loaded has to scroll past five long forms to reach the empty one. What
// is already filled in collapses to a one-line summary instead.
//
// `emptyEntry` is the blank shape of one entry (EMPTY_EXPERIENCE and friends); it
// is copied on every add, never shared.
const useResumeEntryList = (initial, emptyEntry) => {
  const [entries, setEntries] = useState(initial)
  const [expanded, setExpanded] = useState(() => initialExpanded(initial))

  // Append a blank entry and open it — the user asked for it, so it is the one
  // they are about to type into.
  const add = () => {
    setExpanded(entries.length)
    setEntries((previous) => [...previous, { ...emptyEntry }])
  }

  // Immutably patch one field of one entry.
  const patch = (index, key, value) =>
    setEntries((previous) =>
      previous.map((entry, position) =>
        position === index ? { ...entry, [key]: value } : entry,
      ),
    )

  const remove = (index) => {
    setEntries((previous) => previous.filter((_, position) => position !== index))
    // Removing an entry shifts every index after it, so the open card follows its
    // own entry instead of staying on a position that now holds a different one.
    setExpanded((current) => {
      if (current === index) return COLLAPSED
      return current > index ? current - 1 : current
    })
  }

  // Clicking the open card's header closes it, leaving the whole list collapsed.
  const toggle = (index) =>
    setExpanded((current) => (current === index ? COLLAPSED : index))

  // Used when validation fails: an error inside a collapsed card is invisible, so
  // the step opens the offending entry.
  const expand = (index) => setExpanded(index)

  return { entries, expanded, add, patch, remove, toggle, expand }
}

// Open the first entry of a list that failed validation. Its message is rendered
// inside the card, which the accordion may have shut — an error nobody can see is
// worse than no error at all. `found` is the step's per-index error map.
export const expandFirstInvalid = (list, found) => {
  const [first] = Object.keys(found)
  if (first !== undefined) list.expand(Number(first))
}

export default useResumeEntryList
