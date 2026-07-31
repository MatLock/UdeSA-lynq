import './LynqLogo.css'

// The Lynq brand mark: two interlocking rounded brackets — navy opening down-right,
// teal opening up-left — with the teal/violet dot pair nested in the gap between
// them. Traced from the brand asset; colors come from the --logo-* tokens in
// index.css so the glyph stays parametrized like the rest of the palette.
//
// The glyph is sized in `em` (see LynqLogo.css), so wherever it sits next to text
// it scales with the surrounding font-size instead of needing a pixel size per
// call site.
//
// Decorative by default: pass `title` only when the mark stands alone with no
// adjacent "LYNQ" text for a screen reader to pick up.
const LynqLogo = ({ className = '', title }) => (
  <svg
    className={`lynq-logo${className ? ` ${className}` : ''}`}
    viewBox="0 0 100 100"
    role={title ? 'img' : undefined}
    aria-label={title}
    aria-hidden={title ? undefined : true}
    focusable="false"
  >
    <g
      fill="none"
      strokeWidth="15"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M19.5 15.5V69.5H62" stroke="var(--logo-navy)" />
      <path d="M39 37.5H80.5V84.5" stroke="var(--logo-teal)" />
    </g>
    <circle cx="40" cy="52" r="5.8" fill="var(--logo-teal)" />
    <circle cx="60" cy="52" r="6.4" fill="var(--logo-violet)" />
  </svg>
)

export default LynqLogo
