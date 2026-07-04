import strings from '../../i18n'
import './Pagination.css'

// Compact numbered pagination. Pages are zero-based to match the backend's
// PagedRestResponse. Always shows the first and last page and a small window
// around the current one, collapsing the gaps into ellipses so the control
// stays a fixed width regardless of the total page count.
const WINDOW = 1

const buildPageList = (current, total) => {
  const pages = []
  let previous = null
  for (let page = 0; page < total; page += 1) {
    const isEdge = page === 0 || page === total - 1
    const isNearCurrent = Math.abs(page - current) <= WINDOW
    if (!isEdge && !isNearCurrent) continue
    if (previous !== null && page - previous > 1) {
      pages.push({ ellipsis: true, key: `gap-${page}` })
    }
    pages.push({ page, key: `page-${page}` })
    previous = page
  }
  return pages
}

const Pagination = ({ page, totalPages, hasPrevious, hasNext, onPageChange }) => {
  const t = strings.pagination

  if (!totalPages || totalPages <= 1) return null

  const goTo = (target) => {
    if (target < 0 || target >= totalPages || target === page) return
    onPageChange(target)
  }

  return (
    <nav className="pagination" aria-label={t.label}>
      <button
        type="button"
        className="pagination-arrow"
        onClick={() => goTo(page - 1)}
        disabled={!hasPrevious}
        aria-label={t.previous}
      >
        ‹
      </button>

      {buildPageList(page, totalPages).map((item) =>
        item.ellipsis ? (
          <span key={item.key} className="pagination-ellipsis" aria-hidden="true">
            …
          </span>
        ) : (
          <button
            key={item.key}
            type="button"
            className={`pagination-page${item.page === page ? ' is-active' : ''}`}
            onClick={() => goTo(item.page)}
            aria-current={item.page === page ? 'page' : undefined}
          >
            {item.page + 1}
          </button>
        ),
      )}

      <button
        type="button"
        className="pagination-arrow"
        onClick={() => goTo(page + 1)}
        disabled={!hasNext}
        aria-label={t.next}
      >
        ›
      </button>
    </nav>
  )
}

export default Pagination
