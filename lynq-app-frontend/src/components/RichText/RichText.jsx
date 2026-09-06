import './RichText.css'

// Resume prose carries its own structure: one line per bullet, one line per
// paragraph. Rendering it into a single <p> is what made a bulleted role read as
// one run-on sentence, since HTML collapses every run of whitespace.
//
// This is the mirror of lynq-ml's `renderer/rich_text.py`, which does the same
// split for the generated PDF. The two must agree: the candidate sees this on
// screen and the PDF is what they send out, so a resume that reads as a list
// here has to read as a list there.

// The glyphs a resume is bulleted with, ASCII stand-ins included. The marker is
// the author saying "this is a list item"; the glyph itself is presentation, so
// it is dropped and the list draws its own.
const BULLET = /^[-–—*•·▪●◦⁃]\s+/

// A line holding a marker and nothing else — an empty bullet left behind. It is
// dropped without ending the list it sits inside.
const EMPTY_BULLET = /^[-–—*•·▪●◦⁃]$/

// Split the text into consecutive list and paragraph blocks. Blank lines only
// separate blocks, and a run of adjacent bullets becomes one list rather than
// one list each.
const toBlocks = (text) => {
  if (typeof text !== 'string' || text.trim().length === 0) return []

  const blocks = []
  let entries = []

  for (const line of text.split('\n')) {
    const stripped = line.trim()
    if (stripped.length === 0 || EMPTY_BULLET.test(stripped)) continue

    const marker = stripped.match(BULLET)
    if (marker) {
      entries.push(stripped.slice(marker[0].length).trim())
      continue
    }

    if (entries.length > 0) {
      blocks.push({ type: 'list', entries })
      entries = []
    }
    blocks.push({ type: 'paragraph', text: stripped })
  }

  if (entries.length > 0) blocks.push({ type: 'list', entries })

  return blocks
}

// `className` is the class the caller already uses for that kind of text
// ('resume-entry-body', 'resume-summary'); a list carries it too, plus a
// `--list` modifier, so each context can size its own prose. Nothing is
// rendered for empty text, so the caller needs no separate emptiness check.
const RichText = ({ text, className = '' }) => {
  const blocks = toBlocks(text)
  if (blocks.length === 0) return null

  return (
    <>
      {blocks.map((block, index) =>
        block.type === 'list' ? (
          <ul
            key={`${block.type}-${index}`}
            className={`rich-text-list ${className} ${className}--list`}
          >
            {block.entries.map((entry, entryIndex) => (
              <li key={`${entry}-${entryIndex}`}>{entry}</li>
            ))}
          </ul>
        ) : (
          <p
            key={`${block.type}-${index}`}
            className={`rich-text-paragraph ${className}`}
          >
            {block.text}
          </p>
        ),
      )}
    </>
  )
}

export default RichText
