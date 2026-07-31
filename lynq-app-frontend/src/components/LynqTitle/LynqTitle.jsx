import LynqLogo from '../LynqLogo/LynqLogo'
import './LynqTitle.css'

// Capturing group so String.split keeps the brand name as its own chunk.
// No /g flag: .test() below must stay stateless (a global regex would carry
// lastIndex between calls and start skipping matches).
const BRAND = /(lynq)/i

// Renders a heading string with the brand mark next to the brand name. Titles
// come from i18n, so which words are the brand differs per locale — matching on
// the text instead of hard-coding the mark per page means a new locale (or a
// reworded title) gets the logo for free.
//
// Two placements:
//   'inline' (default) — a mark in front of every "LYNQ" the string contains.
//   'leading'          — one mark at the head of the line, copy left untouched.
//                        For titles that open with something else ("Welcome to
//                        LYNQ"), where the logo reads better anchoring the line
//                        than sitting mid-sentence.
//
// Strings without the brand name render as plain text under 'inline', so it is
// safe to use for a whole title/subtitle pair even when only one of them
// mentions Lynq.
const LynqTitle = ({
  text,
  as: Tag = 'span',
  className,
  placement = 'inline',
  ...rest
}) => {
  const label = String(text ?? '')

  if (placement === 'leading') {
    return (
      <Tag className={className} {...rest}>
        <LynqLogo className="lynq-title-lead" />
        {label}
      </Tag>
    )
  }

  const parts = label.split(BRAND)

  return (
    <Tag className={className} {...rest}>
      {parts.map((part, index) =>
        BRAND.test(part) ? (
          // The mark and the word are one lockup — never break the line between them.
          <span className="lynq-title-brand" key={index}>
            <LynqLogo />
            {part}
          </span>
        ) : (
          part
        ),
      )}
    </Tag>
  )
}

export default LynqTitle
