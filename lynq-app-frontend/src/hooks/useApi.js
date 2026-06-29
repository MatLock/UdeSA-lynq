import { useCallback } from 'react'
import useAuth from './useAuth'
import requestUuidUtil from '../utils/requestUuid'

// Base URL for the secured app backend (mirrors registrationService).
const APP_BASE_URL =
  import.meta.env.LYNQ_BACKEND_BASE_URL ?? 'http://localhost:8082/lynq-backend-app'


const useApi = () => {
  const { accessToken, refreshSession, logout } = useAuth()

  const authFetch = useCallback(
    async (path, options = {}) => {
      const url = path.startsWith('http') ? path : `${APP_BASE_URL}${path}`
      const requestUuid = options.requestUuid ?? requestUuidUtil.newRequestUuid()

      const send = (token) =>
        fetch(url, {
          ...options,
          headers: {
            'Content-Type': 'application/json',
            'lynq-request-uuid': requestUuid,
            ...options.headers,
            Authorization: `Bearer ${token}`,
          },
        })

      let response = await send(accessToken)

      // Access token likely expired — refresh once and retry.
      if (response.status === 401) {
        let freshToken
        try {
          freshToken = await refreshSession()
        } catch {
          logout()
          const error = new Error('Session expired. Please log in again.')
          error.status = 401
          throw error
        }
        response = await send(freshToken)
      }

      const payload = await response.json().catch(() => null)

      if (!response.ok) {
        const error = new Error(
          payload?.reason ?? `Request failed with status ${response.status}`,
        )
        error.status = response.status
        error.reason = payload?.reason
        throw error
      }

      return payload
    },
    [accessToken, refreshSession, logout],
  )

  return { authFetch }
}

export default useApi
