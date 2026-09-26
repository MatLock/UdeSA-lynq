import resumeDiff from '../../utils/resumeDiff'
import strings from '../../i18n'
import './ResumeDiff.css'

const SIGN = { added: '+', removed: '−' }

const ResumeDiff = ({ groups }) => {
  const t = strings.jobDetail.tailorDialog.diff
  const sectionNames = strings.pages.resume.sections

  if (groups.length === 0) {
    return <p className="resume-diff-empty">{t.empty}</p>
  }

  return (
    <div className="resume-diff">
      {groups.map((group) => (
        <section
          className="resume-diff-group"
          key={`${group.section}-${group.entry}-${group.lines[0].text}`}
        >
          <h5 className="resume-diff-group-title">
            {sectionNames[group.section]}
            {group.entry && (
              <span className="resume-diff-group-entry">{group.entry}</span>
            )}
          </h5>

          <ul className="resume-diff-lines">
            {group.lines.map((line, index) => (
              <li
                className={`resume-diff-line is-${line.type}`}
                key={`${line.type}-${line.field}-${index}`}
              >
                <span className="resume-diff-sign" aria-hidden="true">
                  {SIGN[line.type]}
                </span>
                <span className="resume-diff-body">
                  <span className="resume-diff-field">
                    <span className="resume-diff-reader-only">
                      {line.type === resumeDiff.ADDED ? t.addedLabel : t.removedLabel}
                    </span>
                    {line.field}
                  </span>
                  <span className="resume-diff-text">
                    {line.parts.map((part, partIndex) => (
                      <span
                        className={part.changed ? 'resume-diff-word is-changed' : undefined}
                        key={`${partIndex}-${part.text}`}
                      >
                        {part.text}
                      </span>
                    ))}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

export default ResumeDiff
