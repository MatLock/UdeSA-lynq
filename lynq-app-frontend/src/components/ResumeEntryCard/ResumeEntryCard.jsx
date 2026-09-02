import { useId } from 'react'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded'
import ErrorOutlineRoundedIcon from '@mui/icons-material/ErrorOutlineRounded'
import './ResumeEntryCard.css'

// One repeatable entry inside a resume-wizard group: a bordered card with its
// position in the list and a remove button, wrapping whatever fields the entry
// needs. Used for jobs, studies, languages, certifications and projects, so the
// card chrome stays identical across all of them.
//
// The card is an accordion panel: its group keeps a single entry expanded (see
// useResumeEntryList) and the rest collapse to their header, so a list of five
// jobs no longer buries the one being written. `summary` is the one-line recap
// shown while collapsed — "Backend Engineer · Acme" — and `invalid` marks a
// collapsed card whose fields failed validation, which would otherwise report an
// error the user cannot see.
const ResumeEntryCard = ({
  title,
  summary,
  removeLabel,
  toggleLabel,
  invalidLabel,
  onRemove,
  expanded,
  invalid = false,
  onToggle,
  children,
}) => {
  const bodyId = useId()
  const className = [
    'resume-entry-card',
    expanded ? '' : 'resume-entry-card--collapsed',
    invalid ? 'resume-entry-card--invalid' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <article className={className}>
      <header className="resume-entry-card-head">
        {/* The whole header is the control, not just the chevron. */}
        <button
          type="button"
          className="resume-entry-card-toggle"
          aria-expanded={expanded}
          aria-controls={bodyId}
          title={toggleLabel}
          onClick={onToggle}
        >
          <ExpandMoreRoundedIcon
            className="resume-entry-card-chevron"
            sx={{ fontSize: 18 }}
          />
          <span className="resume-entry-card-title">{title}</span>
          {/* The recap and the warning only earn their space once the card is
              shut — expanded, the fields themselves say the same thing. */}
          {!expanded && summary && (
            <span className="resume-entry-card-summary">{summary}</span>
          )}
          {!expanded && invalid && (
            <ErrorOutlineRoundedIcon
              className="resume-entry-card-warning"
              aria-label={invalidLabel}
              sx={{ fontSize: 16 }}
            />
          )}
        </button>
        <button
          type="button"
          className="resume-entry-card-remove"
          aria-label={removeLabel}
          title={removeLabel}
          onClick={onRemove}
        >
          <DeleteOutlineRoundedIcon sx={{ fontSize: 16 }} />
        </button>
      </header>
      {/* Unmounted rather than hidden while collapsed: every field is controlled
          by the step's state, so nothing the user typed lives in the DOM. */}
      {expanded && (
        <div id={bodyId} className="resume-entry-card-body">
          {children}
        </div>
      )}
    </article>
  )
}

export default ResumeEntryCard
