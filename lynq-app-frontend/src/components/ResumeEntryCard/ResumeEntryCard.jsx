import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import './ResumeEntryCard.css'

// One repeatable entry inside a resume-wizard group: a bordered card with its
// position in the list and a remove button, wrapping whatever fields the entry
// needs. Used for jobs, studies, languages, certifications and projects, so the
// card chrome stays identical across all of them.
const ResumeEntryCard = ({ title, removeLabel, onRemove, children }) => (
  <article className="resume-entry-card">
    <header className="resume-entry-card-head">
      <span className="resume-entry-card-title">{title}</span>
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
    <div className="resume-entry-card-body">{children}</div>
  </article>
)

export default ResumeEntryCard
