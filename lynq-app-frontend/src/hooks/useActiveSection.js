import { useCallback, useEffect, useState } from 'react'

// Tracks which section of a long, scrollable document is currently in view, and
// exposes a scroll-to helper — the two halves of a "jump to section" navigation.
//
// Sections are discovered from the DOM: any element inside `containerRef` marked
// with `data-section-id` participates, so the caller only has to render the
// attribute. `sectionKey` is an opaque token (typically the joined section ids)
// that re-arms the observer when the set of sections changes — e.g. after
// switching to a resume with different populated sections.
const useActiveSection = (containerRef, sectionKey) => {
  const [activeId, setActiveId] = useState(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const sections = Array.from(container.querySelectorAll('[data-section-id]'))
    if (sections.length === 0) {
      setActiveId(null)
      return
    }

    setActiveId(sections[0].dataset.sectionId)

    // The band is deliberately narrow and near the top of the viewport: the
    // highlighted entry should be the section the reader is actually looking at,
    // not whichever one merely overlaps the (tall) scroll area.
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
        if (visible.length > 0) setActiveId(visible[0].target.dataset.sectionId)
      },
      { root: container, rootMargin: '-8% 0px -75% 0px', threshold: 0 },
    )

    sections.forEach((section) => observer.observe(section))
    return () => observer.disconnect()
  }, [containerRef, sectionKey])

  // Scroll the section into view inside the container (not the page), and light
  // it up immediately so the click feels instant even before the observer fires.
  const scrollTo = useCallback(
    (id) => {
      const target = containerRef.current?.querySelector(`[data-section-id="${id}"]`)
      if (!target) return
      target.scrollIntoView({ behavior: 'smooth', block: 'start' })
      setActiveId(id)
    },
    [containerRef],
  )

  return { activeId, scrollTo }
}

export default useActiveSection
