import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded'
import useResumeWizard from '../../hooks/useResumeWizard'
import strings from '../../i18n'
import './ResumeWizardFooter.css'

// Static action bar for the resume-creation flow. Like RegisterFooter it sits
// below the carousel viewport (not inside the sliding track), so it stays put
// while the active step supplies its buttons through context.footer.
//
// `onCancel` is only passed when there is something to go back to — a candidate
// who already has a resume and chose "create another". On the first-resume path
// there is nowhere to cancel to, so the link is omitted.
const ResumeWizardFooter = ({ onCancel }) => {
  const { footer } = useResumeWizard()
  const t = strings.pages.resume.create

  const { primary, secondary } = footer ?? {}

  return (
    <footer className="resume-footer">
      {primary && (
        <div className="resume-footer-actions">
          {secondary && (
            <button
              type="button"
              className="resume-footer-back"
              onClick={secondary.onClick}
              disabled={secondary.disabled}
            >
              <ArrowBackRoundedIcon sx={{ fontSize: 18 }} />
              {secondary.label}
            </button>
          )}
          <button
            type="button"
            className="resume-footer-next"
            onClick={primary.onClick}
            disabled={primary.disabled}
          >
            {primary.label}
            <ArrowForwardRoundedIcon sx={{ fontSize: 18 }} />
          </button>
        </div>
      )}
      {onCancel && (
        <button type="button" className="resume-footer-cancel" onClick={onCancel}>
          {t.cancel}
        </button>
      )}
    </footer>
  )
}

export default ResumeWizardFooter
