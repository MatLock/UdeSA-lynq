import { useEffect, useState } from 'react'

const useAnalyticsQuery = (query) => {
  const [state, setState] = useState({ status: 'loading', data: null })

  useEffect(() => {
    let cancelled = false

    const load = async () => {
      setState((previous) => ({ status: 'loading', data: previous.data }))
      try {
        const data = await query()
        if (!cancelled) setState({ status: data ? 'ready' : 'unavailable', data })
      } catch {
        if (!cancelled) setState({ status: 'unavailable', data: null })
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [query])

  return state
}

export default useAnalyticsQuery
