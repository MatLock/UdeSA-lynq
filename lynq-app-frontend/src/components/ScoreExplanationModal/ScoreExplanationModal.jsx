import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import SchoolOutlinedIcon from '@mui/icons-material/SchoolOutlined'
import strings from '../../i18n'
import Spinner from '../Spinner/Spinner.jsx'
import './ScoreExplanationModal.css'

// A closable overlay modal explaining the candidate's LYNQ score for one of
// their applications, plus the courses lynq-ml recommends to close each skill
// gap (GET /user/upskilling-suggestion/{jobId}, see
// userService.get_upskilling_suggestion). Portals to <body> (like
// CandidateEvaluationModal) so it isn't clipped by the page's overflow, dims the
// backdrop, and closes on the ×, a backdrop click, or Escape. Fetching/error
// state is owned by the parent and passed in: while `loading`, a spinner shows;
// on `error`, the error copy; otherwise `result` ({ outcome, suggestions }).
const ScoreExplanationModal = ({ jobTitle, result, loading, error, onClose }) => {
  const t = strings.pages.applications

  useEffect(() => {
    const onKey = (event) => {
      if (event.key === 'Escape') onClose?.()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  // Only suggestions that actually carry courses are worth a section.
  const suggestions = (result?.suggestions ?? []).filter(
    (suggestion) => suggestion.courses?.length > 0,
  )

  return createPortal(
    <div className="score-modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="score-modal"
        role="dialog"
        aria-modal="true"
        aria-label={t.explanationTitle}
        onClick={(event) => event.stopPropagation()}
      >
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

        {loading ? (
          <div className="score-modal-state">
            <Spinner label={t.explanationLoading} />
          </div>
        ) : error ? (
          <p className="score-modal-state score-modal-error">{t.explanationError}</p>
        ) : (
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
                  {result.reasons.map((reason, index) => (
                    <li key={index}>{reason}</li>
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
                suggestions.map((suggestion, index) => (
                  <div className="score-modal-suggestion" key={index}>
                    {suggestion.query && (
                      <span className="score-modal-query">{suggestion.query}</span>
                    )}
                    <ul className="score-modal-courses">
                      {suggestion.courses.map((course, courseIndex) => (
                        <li key={courseIndex}>
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
        )}
      </div>
    </div>,
    document.body,
  )
}

export default ScoreExplanationModal
