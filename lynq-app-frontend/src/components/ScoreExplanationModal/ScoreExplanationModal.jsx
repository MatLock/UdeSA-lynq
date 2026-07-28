import { useCallback, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import SchoolOutlinedIcon from '@mui/icons-material/SchoolOutlined'
import strings from '../../i18n'
import Spinner from '../Spinner/Spinner.jsx'
import './ScoreExplanationModal.css'

// A closable overlay modal explaining the candidate's LYNQ score for one of
// their applications, plus the courses lynq-ml recommends to close each skill
// gap (GET /user/upskilling-suggestion/{jobId}, see
// userService.get_upskilling_suggestion). A native modal <dialog> portaled to
// <body> (like CandidateEvaluationModal) so it isn't clipped by the page's
// overflow; the browser owns the backdrop, the focus trap and Escape, and it
// closes on the ×, a backdrop click, or Escape. Fetching/error state is owned
// by the parent and passed in: while `loading`, a spinner shows; on `error`,
// the error copy; otherwise `result` ({ outcome, suggestions }).
const ScoreExplanationModal = ({ jobTitle, result, loading, error, onClose }) => {
  const t = strings.pages.applications
  const dialogRef = useRef(null)

  // showModal() is what puts the element in the top layer and paints the
  // backdrop. Opening on the ref callback ties it to the element's own
  // lifetime, so a re-render (e.g. `loading` flipping) can't close and reopen
  // it. The callback is stable so React attaches it exactly once.
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

  // Only suggestions that actually carry courses are worth a section.
  const suggestions = (result?.suggestions ?? []).filter(
    (suggestion) => suggestion.courses?.length > 0,
  )

  const renderBody = () => {
    if (loading) {
      return (
        <div className="score-modal-state">
          <Spinner label={t.explanationLoading} />
        </div>
      )
    }

    if (error) {
      return <p className="score-modal-state score-modal-error">{t.explanationError}</p>
    }

    return (
      <div className="score-modal-body">
        {result?.outcome && (
          <div className="score-modal-section">
            <h4 className="score-modal-section-title">{t.explanationSummary}</h4>
            <p className="score-modal-explanation">{result.outcome}</p>
          </div>
        )}

        {result?.reasons?.length > 0 && (
          <div className="score-modal-section">
            <h4 className="score-modal-section-title">{t.explanationReasons}</h4>
            <ul className="score-modal-reasons">
              {result.reasons.map((reason) => (
                <li key={reason}>{reason}</li>
              ))}
            </ul>
          </div>
        )}

        <div className="score-modal-section">
          <h4 className="score-modal-section-title">
            <SchoolOutlinedIcon sx={{ fontSize: 16 }} />
            {t.recommendedCourses}
          </h4>

          {suggestions.length > 0 ? (
            suggestions.map((suggestion) => (
              <div className="score-modal-suggestion" key={suggestion.query}>
                {suggestion.query && (
                  <span className="score-modal-query">{suggestion.query}</span>
                )}
                <ul className="score-modal-courses">
                  {suggestion.courses.map((course) => (
                    <li key={course.url ?? course.title}>
                      {course.url ? (
                        <a
                          href={course.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="score-modal-course-link"
                        >
                          {course.title ?? course.url}
                        </a>
                      ) : (
                        <span>{course.title}</span>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            ))
          ) : (
            <p className="score-modal-empty">{t.explanationNoCourses}</p>
          )}
        </div>
      </div>
    )
  }

  return createPortal(
    <dialog ref={attachDialog} className="score-modal" aria-label={t.explanationTitle}>
      <div className="score-modal-content">
        <div className="score-modal-header">
          <span className="score-modal-title">
            <AutoAwesomeIcon sx={{ fontSize: 20 }} />
            {t.explanationTitle}
          </span>
          <button
            type="button"
            className="score-modal-close"
            onClick={onClose}
            aria-label={t.explanationClose}
          >
            ×
          </button>
        </div>

        {jobTitle && <p className="score-modal-subject">{jobTitle}</p>}

        {renderBody()}
      </div>
    </dialog>,
    document.body,
  )
}

export default ScoreExplanationModal
