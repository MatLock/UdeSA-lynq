import { useState } from 'react'
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined'
import strings from '../../i18n'
import './TagInput.css'

// Free-form list editor for the string arrays a resume carries (skills, tools,
// achievements). Entries are shown as removable chips above the input and are
// committed by Enter, by a comma, or with the explicit "Add" button — the button
// matters because a keyboard-only affordance isn't discoverable.
//
// Controlled by `value` + `onChange`: the list belongs to the step's form state,
// only the half-typed text is local.
const TagInput = ({ id, value, onChange, placeholder, tone = 'blue' }) => {
  const t = strings.pages.resume.create
  const [draft, setDraft] = useState('')

  // Append entries, keeping order and dropping blanks / case-insensitive
  // duplicates (matches how skill chips are merged elsewhere in the app).
  const commit = () => {
    if (!draft.trim()) return
    const seen = new Set(value.map((item) => item.toLowerCase()))
    const merged = [...value]
    for (const token of draft.split(',')) {
      const entry = token.trim()
      if (entry && !seen.has(entry.toLowerCase())) {
        seen.add(entry.toLowerCase())
        merged.push(entry)
      }
    }
    onChange(merged)
    setDraft('')
  }

  const handleKeyDown = (event) => {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      commit()
    }
  }

  return (
    <div className="tag-input">
      {value.length > 0 && (
        <ul className={`tag-input-chips tone-${tone}`}>
          {value.map((item) => (
            <li key={item} className="tag-input-chip">
              {item}
              <button
                type="button"
                className="tag-input-remove"
                aria-label={`${t.remove}: ${item}`}
                onClick={() => onChange(value.filter((entry) => entry !== item))}
              >
                <CloseOutlinedIcon sx={{ fontSize: 13 }} />
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="tag-input-row">
        <input
          id={id}
          value={draft}
          placeholder={placeholder}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={handleKeyDown}
          onBlur={commit}
        />
        <button
          type="button"
          className="tag-input-add"
          onClick={commit}
          disabled={!draft.trim()}
        >
          {t.add}
        </button>
      </div>
    </div>
  )
}

export default TagInput
