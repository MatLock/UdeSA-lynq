import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'
import strings from '../../i18n'
import './CandidateEvaluationModal.css'

// Normalize the free-text lynq-ml recommendation to a stable slug so the badge
// can be colored and (when it matches a known verdict) translated.
const recommendationSlug = (recommendation) =>
  (recommendation ?? '').trim().toLowerCase().replace(/[\s-]+/g, '_')

// A closable overlay modal that shows the AI hiring evaluation for one
// candidate. Portals to <body> (like Toast) so it isn't clipped by the page's
// overflow-hidden layout, dims the backdrop, and closes on the ×, a backdrop
// click, or Escape. Renders nothing when there is no result to show.
const CandidateEvaluationModal = ({ candidateName, result, onClose }) => {
  const t = strings.pages.jobCandidates

  useEffect(() => {
    const onKey = (event) => {
      if (event.key === 'Escape') onClose?.()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  if (!result) return null

  const slug = recommendationSlug(result.recommendation)
  const recLabel = t.aiRecommendation[slug] ?? result.recommendation

  return createPortal(
    <div className="candidate-modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="candidate-modal"
        role="dialog"
        aria-modal="true"
        aria-label={t.aiEvaluation}
        onClick={(event) => event.stopPropagation()}
      >
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
                {result.strengths.map((item, index) => (
                  <li key={index}>{item}</li>
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
                {result.concerns.map((item, index) => (
                  <li key={index}>{item}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </div>,
    document.body,
  )
}

export default CandidateEvaluationModal
