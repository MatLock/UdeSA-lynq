const ADDED = 'added'
const REMOVED = 'removed'
const REWRITTEN = 'rewritten'

const samePlace = (left, right) =>
  left.section === right.section &&
  left.entry === right.entry &&
  left.field === right.field

const rowOf = (line, kind, before, after) => ({
  kind,
  section: line.section,
  entry: line.entry,
  field: line.field,
  before,
  after,
})

const resumeChangeRows = (groups) => {
  const rows = []

  ;(groups ?? []).forEach((group) => {
    const lines = group.lines ?? []
    let index = 0

    while (index < lines.length) {
      const line = lines[index]
      const next = lines[index + 1]

      if (line.type === REMOVED && next?.type === ADDED && samePlace(line, next)) {
        rows.push(rowOf(line, REWRITTEN, line, next))
        index += 2
        continue
      }

      rows.push(
        line.type === ADDED
          ? rowOf(line, ADDED, null, line)
          : rowOf(line, REMOVED, line, null),
      )
      index += 1
    }
  })

  return rows
}

export default resumeChangeRows
export { ADDED, REMOVED, REWRITTEN }
