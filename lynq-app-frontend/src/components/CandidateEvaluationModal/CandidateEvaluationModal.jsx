import { createPortal } from 'react-dom'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'
import strings from '../../i18n'
import useModalDialog from '../../hooks/useModalDialog'
import './CandidateEvaluationModal.css'

// Normalize the free-text lynq-ml recommendation to a stable slug so the badge
// can be colored and (when it matches a known verdict) translated.
const recommendationSlug = (recommendation) =>
  (recommendation ?? '').trim().toLowerCase().replace(/[\s-]+/g, '_')

// A closable overlay modal that shows the AI hiring evaluation for one
// candidate. Rendered as a native modal <dialog>, so the browser owns the
// backdrop, the focus trap and Escape. Portals to <body> (like Toast) so it
// isn't clipped by the page's overflow-hidden layout, and closes on the ×, a
// backdrop click, or Escape. Renders nothing when there is no result to show.
const CandidateEvaluationModal = ({ candidateName, result, onClose }) => {
  const t = strings.pages.jobCandidates
  const attachDialog = useModalDialog(onClose)

  if (!result) return null

  const slug = recommendationSlug(result.recommendation)
  const recLabel = t.aiRecommendation[slug] ?? result.recommendation

  return createPortal(
    <dialog ref={attachDialog} className="candidate-modal" aria-label={t.aiEvaluation}>
      <div className="candidate-modal-content">
        <div className="candidate-modal-header">
          <span className="candidate-modal-title">
            <AutoAwesomeOutlinedIcon sx={{ fontSize: 20 }} />
            {t.aiEvaluation}
          </span>
          <button
            type="button"
            className="candidate-modal-close"
            onClick={onClose}
            aria-label={t.aiClose}
          >
            ×
          </button>
        </div>

        {candidateName && <p className="candidate-modal-subject">{candidateName}</p>}

        {recLabel && (
          <span className={`candidate-modal-badge candidate-modal-badge-${slug}`}>
            {recLabel}
          </span>
        )}

        <div className="candidate-modal-body">
          {result.explanation && (
            <p className="candidate-modal-explanation">{result.explanation}</p>
          )}

          {result.strengths?.length > 0 && (
            <div className="candidate-modal-section">
              <h4 className="candidate-modal-section-title candidate-modal-strengths">
                {t.aiStrengths}
              </h4>
              <ul className="candidate-modal-list">
                {result.strengths.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          )}

          {result.concerns?.length > 0 && (
            <div className="candidate-modal-section">
              <h4 className="candidate-modal-section-title candidate-modal-concerns">
                {t.aiConcerns}
              </h4>
              <ul className="candidate-modal-list">
                {result.concerns.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </dialog>,
    document.body,
  )
}

export default CandidateEvaluationModal
