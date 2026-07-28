import AddRoundedIcon from '@mui/icons-material/AddRounded'
import './ResumeStepGroup.css'

// A titled block inside a resume-wizard step — "Studies", "Languages", "Jobs" —
// with the button that appends a new entry and the placeholder shown while the
// block is still empty.
//
// Each of the last three steps holds two or three of these, so the heading/add/
// empty chrome is defined once here rather than restated per step. A group whose
// header action isn't "add another" (the skills group generates them with AI)
// passes it as `action` instead of `onAdd`.
const ResumeStepGroup = ({
  title,
  addLabel,
  onAdd,
  action,
  emptyLabel,
  isEmpty = false,
  children,
}) => (
  <section className="resume-group">
    <header className="resume-group-head">
      <h3 className="resume-group-title">{title}</h3>
      {onAdd && (
        <button type="button" className="resume-group-add" onClick={onAdd}>
          <AddRoundedIcon sx={{ fontSize: 16 }} />
          {addLabel}
        </button>
      )}
      {action}
    </header>

    {isEmpty ? <p className="resume-group-empty">{emptyLabel}</p> : children}
  </section>
)

export default ResumeStepGroup
