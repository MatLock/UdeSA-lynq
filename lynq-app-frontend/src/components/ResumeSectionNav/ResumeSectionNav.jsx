import PersonOutlineRoundedIcon from '@mui/icons-material/PersonOutlineRounded'
import NotesRoundedIcon from '@mui/icons-material/NotesRounded'
import WorkOutlineRoundedIcon from '@mui/icons-material/WorkOutlineRounded'
import SchoolOutlinedIcon from '@mui/icons-material/SchoolOutlined'
import PsychologyOutlinedIcon from '@mui/icons-material/PsychologyOutlined'
import TranslateRoundedIcon from '@mui/icons-material/TranslateRounded'
import WorkspacePremiumOutlinedIcon from '@mui/icons-material/WorkspacePremiumOutlined'
import RocketLaunchOutlinedIcon from '@mui/icons-material/RocketLaunchOutlined'
import strings from '../../i18n'
import './ResumeSectionNav.css'

// One icon per resume section id (see utils/resumeSections).
const ICONS = {
  personal: PersonOutlineRoundedIcon,
  summary: NotesRoundedIcon,
  experience: WorkOutlineRoundedIcon,
  education: SchoolOutlinedIcon,
  skills: PsychologyOutlinedIcon,
  languages: TranslateRoundedIcon,
  certifications: WorkspacePremiumOutlinedIcon,
  projects: RocketLaunchOutlinedIcon,
}

// The resume viewer's jump-to-section rail. It only lists the sections the
// resume actually fills in (the caller passes them in already filtered), marks
// the one currently in view, and shows how many entries each list section holds.
// Purely presentational: scroll tracking lives in useActiveSection, so this
// component just reports clicks and paints the active state.
const ResumeSectionNav = ({ sections, activeId, onSelect }) => {
  const t = strings.pages.resume

  if (sections.length === 0) return null

  return (
    <nav className="resume-nav" aria-label={t.pickResume}>
      <ul className="resume-nav-list">
        {sections.map(({ id, count }) => {
          const Icon = ICONS[id] ?? NotesRoundedIcon
          const active = id === activeId
          return (
            <li key={id}>
              <button
                type="button"
                className={active ? 'resume-nav-item active' : 'resume-nav-item'}
                aria-current={active ? 'true' : undefined}
                onClick={() => onSelect(id)}
              >
                <span className="resume-nav-icon">
                  <Icon sx={{ fontSize: 17 }} />
                </span>
                <span className="resume-nav-label">{t.sections[id]}</span>
                {count > 0 && <span className="resume-nav-count">{count}</span>}
              </button>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

export default ResumeSectionNav
