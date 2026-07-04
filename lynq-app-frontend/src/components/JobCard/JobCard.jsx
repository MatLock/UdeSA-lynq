import strings from '../../i18n'
import UserIcon from '../UserIcon/UserIcon.jsx'
import './JobCard.css'

// A single available job post. Layout mirrors the product spec: a centered
// title with a colored work-type tag, a description clipped to a preview length,
// the posting user's identity, and a footer of key skills alongside an Apply
// action. Presentational only — the caller owns what Apply does.
const MAX_DESCRIPTION_LENGTH = 100
const MAX_SKILLS = 3

// Clip to a preview length, appending an ellipsis only when text was actually
// dropped so short descriptions stay untouched.
const truncate = (text, max) =>
  text && text.length > max ? `${text.slice(0, max).trimEnd()}…` : text ?? ''

const JobCard = ({ job, onApply }) => {
  const t = strings.jobCard
  const workTypeLabel = t.workType[job.workType] ?? job.workType
  const skills = (job.skills ?? []).slice(0, MAX_SKILLS)
  const poster = job.postedBy

  return (
    <article className="job-card">
      <header className="job-card-header">
        <h3 className="job-card-title">{job.title}</h3>
        <span
          className={`job-card-worktype job-card-worktype-${(
            job.workType ?? ''
          ).toLowerCase()}`}
        >
          {workTypeLabel}
        </span>
      </header>

      <p className="job-card-description">
        {truncate(job.description, MAX_DESCRIPTION_LENGTH)}
      </p>

      <div className="job-card-poster">
        <span className="job-card-poster-label">{t.postedBy}</span>
        <span className="job-card-poster-avatar">
          {poster?.profileImageUrl ? (
            <img src={poster.profileImageUrl} alt={poster.fullName ?? ''} />
          ) : (
            <UserIcon />
          )}
        </span>
        <span className="job-card-poster-name">
          {poster?.fullName ?? t.unknownPoster}
        </span>
      </div>

      <footer className="job-card-footer">
        <ul className="job-card-skills">
          {skills.map((skill) => (
            <li key={skill} className="job-card-skill">
              {skill}
            </li>
          ))}
        </ul>
        <button
          type="button"
          className="job-card-apply"
          onClick={() => onApply?.(job)}
        >
          {t.apply}
        </button>
      </footer>
    </article>
  )
}

export default JobCard
