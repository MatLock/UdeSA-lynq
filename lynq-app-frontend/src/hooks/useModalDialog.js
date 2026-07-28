import { useCallback, useEffect, useRef } from 'react'

// Wires up a native modal <dialog> so the browser owns the backdrop, the focus
// trap and Escape, while the parent keeps owning the open/closed state.
//
// Returns the ref callback to hand to the <dialog>. showModal() is what puts
// the element in the top layer and paints the backdrop; opening on the ref
// callback ties it to the element's own lifetime, so a re-render can't close
// and reopen it. The callback is stable, so React attaches it exactly once.
const useModalDialog = (onClose) => {
  const dialogRef = useRef(null)

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

  return attachDialog
}

export default useModalDialog
