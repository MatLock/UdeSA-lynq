import { useEffect, useRef } from 'react'
import reduxDevtools from '../utils/reduxDevtools'

// Pushes a named slice of state into the Redux DevTools extension whenever its
// (serializable) value changes. Development-only and a no-op when the extension
// is absent — see utils/reduxDevtools. Dedupes by content so it only emits an
// action on a real change, not on every render.
const useReduxDevtools = (action, state) => {
  const lastRef = useRef(null)

  useEffect(() => {
    if (!reduxDevtools.isEnabled()) return
    const snapshot = JSON.stringify(state)
    if (snapshot === lastRef.current) return
    lastRef.current = snapshot
    reduxDevtools.send(action, state)
  })
}

export default useReduxDevtools
