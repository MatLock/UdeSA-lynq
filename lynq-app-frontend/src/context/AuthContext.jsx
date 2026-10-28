import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import authService from '../services/authService'
import userService from '../services/userService'
import securedFetch from '../utils/securedFetch'
import accessTokenRoles from '../utils/accessTokenRoles'
import profileImageCache from '../utils/profileImageCache'
import fileToDataUrl from '../utils/fileToDataUrl'
import useReduxDevtools from '../hooks/useReduxDevtools'

// Holds the authenticated session: the access token and the user identity.
// RequireAuth treats a session as valid only when both are set.
//
// No token is ever written to browser storage. The refresh token lives in the
// HttpOnly `lynq_refresh` cookie the gateway sets at login, which no script can
// read; the short-lived access token lives only in memory and is minted fresh
// from that cookie (/auth/refresh) on each load. Storage keeps just the user, as
// a hint that there is a session to rehydrate, in the storage chosen by
// "remember me" at login time:
//  - remember me ON  -> localStorage, next to a persistent cookie.
//  - remember me OFF -> sessionStorage, next to a session cookie: both are
//    cleared when the browser closes.
const AuthContext = createContext(null)

const STORAGE_KEY = 'lynq.auth'

const readStored = (storage) => {
  try {
    const raw = storage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

const readHint = () => {
  const inTab = readStored(sessionStorage)
  if (inTab?.user) return { user: inTab.user, remembered: false }
  const persisted = readStored(localStorage)
  if (persisted?.user) return { user: persisted.user, remembered: true }
  return null
}

const persistSession = (session) => {
  const hint = JSON.stringify({ user: session.user })
  if (session.remembered) {
    localStorage.setItem(STORAGE_KEY, hint)
    sessionStorage.removeItem(STORAGE_KEY)
  } else {
    sessionStorage.setItem(STORAGE_KEY, hint)
    localStorage.removeItem(STORAGE_KEY)
  }
}

const clearStoredSession = () => {
  localStorage.removeItem(STORAGE_KEY)
  sessionStorage.removeItem(STORAGE_KEY)
}

// Merge an app-backend profile (GetUserRestResponse from GET /user) into a base
// user. The avatar field is normalized to `profileImageUrl` so consumers (e.g.
// the sidebar) read a single name. Returns the base user unchanged when no
// profile is given.
const withProfile = (user, profile) =>
  profile
    ? {
        ...user,
        fullName: profile.fullName,
        // The backend URL is a pre-signed link that expires after 15 minutes;
        // prefer a locally cached image (from a prior upload on this device) so
        // the avatar stays visible past that window. Falls back to the backend
        // URL when nothing is cached.
        profileImageUrl:
          profileImageCache.read(user.id) ?? profile.userProfileImageUrl,
        // COMPANY owners carry the id of the company they own; the sidebar uses
        // it to open their own company at /company/{companyId}.
        companyId: profile.companyId,
        currentPosition: profile.currentPosition,
        about: profile.about,
        githubUrl: profile.githubUrl,
        linkedinUrl: profile.linkedinUrl,
        birthDate: profile.birthDate,
        expectedSalary: profile.expectedSalary,
        expectedSalaryCurrency: profile.expectedSalaryCurrency,
      }
    : user

const AuthProvider = ({ children }) => {
  const [session, setSession] = useState(null)
  // Bootstrap is only needed when a stored user hints at a session whose
  // refresh cookie can be traded for an access token via /auth/refresh.
  const [loading, setLoading] = useState(() => Boolean(readHint()))

  // Latest session, readable from stable callbacks without re-creating them.
  const sessionRef = useRef(session)
  useEffect(() => {
    sessionRef.current = session
  }, [session])

  // Merge partial fields into the stored user and persist. Consumers (e.g. the
  // sidebar) react immediately — used to reflect a freshly picked profile image
  // before the backend update round-trips.
  const updateUser = useCallback((partial) => {
    const current = sessionRef.current
    if (!current) return
    const next = { ...current, user: { ...current.user, ...partial } }
    persistSession(next)
    setSession(next)
  }, [])

  // Replace the session's access token (preserving the remembered flag), e.g.
  // after a password change that rotates both tokens server-side; the new
  // refresh token arrives in the response's HttpOnly cookie.
  const applyTokens = useCallback(({ accessToken }) => {
    const current = sessionRef.current
    if (!current) return
    setSession({ ...current, accessToken })
  }, [])

  // Reconcile the locally cached avatar with the pre-signed URL the backend
  // hands back at login/refresh. That URL is short-lived and its signature
  // changes on every request, so we can't compare URLs — we fetch the image
  // bytes and compare them (as a data URL) against the cache. On any difference
  // (a first-ever image, or one changed from another device) we refresh the
  // cache and the displayed avatar. Best-effort: if the image can't be fetched
  // (offline, CORS, no image) the existing cache stands.
  const syncProfileImageCache = useCallback(
    async (userId, backendImageUrl) => {
      if (!userId || !backendImageUrl) return
      try {
        const response = await fetch(backendImageUrl)
        if (!response.ok) return
        const dataUrl = await fileToDataUrl(await response.blob())
        if (!dataUrl || dataUrl === profileImageCache.read(userId)) return
        profileImageCache.write(userId, dataUrl)
        // Only touch the displayed avatar if this is still the active user.
        if (sessionRef.current?.user?.id === userId) {
          updateUser({ profileImageUrl: dataUrl })
        }
      } catch {
        // Network/CORS error or the image is gone — keep the current cache.
      }
    },
    [updateUser],
  )

  // Dedupes concurrent refreshes: many in-flight requests that 401 at once share
  // a single /auth/refresh call instead of each firing their own.
  const refreshPromiseRef = useRef(null)

  // On load, rehydrate the stored session by minting a fresh access token
  // from the refresh cookie.
  useEffect(() => {
    if (session) return

    const hint = readHint()
    // `loading` is initialized false unless a stored user exists, so there's
    // nothing to settle here when one is absent.
    if (!hint) return

    let cancelled = false
    const rehydrate = async () => {
      try {
        const accessToken = await authService.refresh_access_token()
        if (cancelled) return

        // Refresh the profile so a rehydrated session doesn't show stale data.
        // Non-fatal: fall back to the stored user if the lookup fails.
        let user = hint.user
        let profile = null
        try {
          // Pre-session bootstrap: the access token was just minted, so a plain
          // token-bound fetcher (no refresh loop) is the right fit here.
          profile = await userService.get_user(securedFetch.tokenFetcher(accessToken))
          user = withProfile(hint.user, profile)
        } catch {
          // Keep the stored user — backend unreachable or no profile.
        }
        if (cancelled) return

        setSession({ accessToken, user, remembered: hint.remembered })
        // Refresh the cached avatar if the backend's image differs from cache.
        void syncProfileImageCache(user.id, profile?.userProfileImageUrl)
      } catch {
        // Refresh cookie missing, expired or invalid — drop the stored session.
        if (!cancelled) {
          clearStoredSession()
          setSession(null)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    rehydrate()

    return () => {
      cancelled = true
    }
    // Run once on mount; the initial `session` value is read synchronously above.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Accepts the UserRestResponse returned by authService login/register:
  // { id, username, email, creationDate, accessToken }. The refresh token is
  // not in it: the gateway set it as an HttpOnly cookie on the same response.
  //
  // `profile` is the optional GetUserRestResponse from the app-backend (GET
  // /user). When present its fields are merged into the stored user so consumers
  // (e.g. the sidebar) get fullName / profileImageUrl without a second lookup.
  const login = useCallback((payload, rememberMe = false, profile = null) => {
    const next = {
      accessToken: payload.accessToken,
      user: withProfile(
        {
          id: payload.id,
          username: payload.username,
          email: payload.email,
          creationDate: payload.creationDate,
        },
        profile,
      ),
      remembered: rememberMe,
    }
    persistSession(next)
    setSession(next)
    setLoading(false)
    // Refresh the cached avatar from the backend's fresh pre-signed URL if the
    // image changed since it was last cached on this device.
    void syncProfileImageCache(next.user.id, profile?.userProfileImageUrl)
  }, [syncProfileImageCache])

  const logout = useCallback(() => {
    clearStoredSession()
    refreshPromiseRef.current = null
    setSession(null)
    void authService.end_session()
  }, [])

  // Mint a fresh access token from the refresh cookie, keep it in memory, and
  // return it. Concurrent calls share one in-flight request.
  const refreshSession = useCallback(async () => {
    if (refreshPromiseRef.current) return refreshPromiseRef.current

    const run = (async () => {
      const current = sessionRef.current
      if (!current) {
        throw new Error('No session to refresh')
      }
      const accessToken = await authService.refresh_access_token()
      const next = { ...current, accessToken }
      setSession(next)
      return accessToken
    })()

    refreshPromiseRef.current = run
    try {
      return await run
    } finally {
      refreshPromiseRef.current = null
    }
  }, [])

  const value = useMemo(() => {
    const roles = accessTokenRoles.rolesOf(session?.accessToken)
    return {
      accessToken: session?.accessToken ?? null,
      user: session?.user ?? null,
      remembered: session?.remembered ?? false,
      roles,
      isCompany: roles.includes(accessTokenRoles.COMPANY),
      isAuthenticated: Boolean(session?.accessToken && session?.user),
      loading,
      login,
      logout,
      updateUser,
      applyTokens,
      refreshSession,
    }
  }, [session, loading, login, logout, updateUser, applyTokens, refreshSession])

  // Mirror the auth state into the Redux DevTools extension (dev only). Tokens
  // are reduced to presence flags to keep the inspector readable and avoid
  // dumping raw credentials into its timeline.
  useReduxDevtools('auth', {
    isAuthenticated: value.isAuthenticated,
    loading,
    user: value.user,
    roles: value.roles,
    remembered: session?.remembered ?? false,
    hasAccessToken: Boolean(session?.accessToken),
  })

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export { AuthContext }
export default AuthProvider
