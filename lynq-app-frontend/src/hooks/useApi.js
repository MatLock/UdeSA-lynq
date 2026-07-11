import { useCallback } from 'react'
import useAuth from './useAuth'
import requestUuidUtil from '../utils/requestUuid'
import securedFetch from '../utils/securedFetch'

// Authenticated fetcher for in-session pages/components. Delegates the actual
// request to securedFetch.sendSecured and adds the piece a bare token can't
// provide: when the access token has expired (backend replies 401), it refreshes
// the token via the session and retries the request once before surfacing the
// error. Concurrent refreshes are deduped inside AuthContext.refreshSession.
const useApi = () => {
  const { accessToken, refreshSession, logout } = useAuth()

  const authFetch = useCallback(
    async (path, options = {}) => {
      // A caller may pin a correlation id via options.requestUuid so a multi-call
      // flow traces as one; it isn't a real fetch option, so keep it out of them.
      const { requestUuid: pinnedUuid, ...fetchOptions } = options
      const requestUuid = pinnedUuid ?? requestUuidUtil.newRequestUuid()

      try {
        return await securedFetch.sendSecured(accessToken, path, fetchOptions, requestUuid)
      } catch (error) {
        if (error.status !== 401) throw error

        // Access token expired — refresh once and retry with the fresh token.
        let freshToken
        try {
          freshToken = await refreshSession()
        } catch {
          logout()
          const sessionError = new Error('Session expired. Please log in again.')
          sessionError.status = 401
          throw sessionError
        }
        return await securedFetch.sendSecured(freshToken, path, fetchOptions, requestUuid)
      }
    },
    [accessToken, refreshSession, logout],
  )

  return { authFetch }
}

export default useApi
