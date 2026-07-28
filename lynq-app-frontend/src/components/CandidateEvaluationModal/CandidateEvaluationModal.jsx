import { useCallback, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'
import strings from '../../i18n'
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
  const dialogRef = useRef(null)

  // showModal() is what puts the element in the top layer and paints the
  // backdrop. Opening on the ref callback ties it to the element's own
  // lifetime, so a re-render can't close and reopen it. The callback is stable
  // so React attaches it exactly once.
  const attachDialog = useCallback((node) => {
    dialogRef.current = node
    if (node && !node.open) node.showModal()
  }, [])

  // Listeners are wired imperatively rather than as JSX props: a <dialog> is a
  // non-interactive element, so React handlers on it would be an a11y smell.
  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return undefined

    // A click on the ::backdrop is reported with the dialog itself as target;
    // clicks on the content land on the inner wrapper instead.
    const onBackdropClick = (event) => {
      if (event.target === dialog) onClose?.()
    }
    // Escape would close the element on its own, leaving the parent's state
    // stale — intercept it and let the parent drive the unmount.
    const onCancel = (event) => {
      event.preventDefault()
      onClose?.()
    }

    dialog.addEventListener('click', onBackdropClick)
    dialog.addEventListener('cancel', onCancel)

    return () => {
      dialog.removeEventListener('click', onBackdropClick)
      dialog.removeEventListener('cancel', onCancel)
    }
  }, [onClose])

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
