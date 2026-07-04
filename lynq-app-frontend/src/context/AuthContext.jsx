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
import profileImageCache from '../utils/profileImageCache'
import fileToDataUrl from '../utils/fileToDataUrl'
import useReduxDevtools from '../hooks/useReduxDevtools'

// Holds the authenticated session: the access/refresh tokens and the user
// identity. RequireAuth treats a session as valid only when all three are set.
//
// Persistence depends on "remember me" at login time:
//  - remember me ON  -> localStorage keeps only the durable bits { user,
//    refreshToken }. The short-lived access token is NOT written to disk; it is
//    minted fresh from the refresh token (/auth/refresh) on each load.
//  - remember me OFF -> the full session lives in sessionStorage: it survives a
//    reload within the tab but is cleared when the tab/browser closes.
const AuthContext = createContext(null)

const STORAGE_KEY = 'lynq.auth'

// Full session ({ accessToken, refreshToken, user }) from a non-remembered login.
const readSession = () => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

// Durable bits ({ refreshToken, user }) from a remembered login.
const readPersisted = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

// Write the session to the storage chosen by its `remembered` flag. A remembered
// session persists only the durable bits (the access token stays in memory);
// otherwise the full session goes to sessionStorage.
const persistSession = (session) => {
  if (session.remembered) {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ refreshToken: session.refreshToken, user: session.user }),
    )
    sessionStorage.removeItem(STORAGE_KEY)
  } else {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    localStorage.removeItem(STORAGE_KEY)
  }
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
        userType: profile.userType,
        currentPosition: profile.currentPosition,
        about: profile.about,
        githubUrl: profile.githubUrl,
        linkedinUrl: profile.linkedinUrl,
        birthDate: profile.birthDate,
      }
    : user

const AuthProvider = ({ children }) => {
  const [session, setSession] = useState(readSession)
  // Bootstrap is only needed when there's no in-tab session but there may be a
  // remembered one to rehydrate via /auth/refresh.
  const [loading, setLoading] = useState(
    () => !readSession() && Boolean(readPersisted()?.refreshToken),
  )

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

  // Replace the session's access/refresh tokens (preserving the remembered
  // flag), e.g. after a password change that rotates both tokens server-side.
  const applyTokens = useCallback(({ accessToken, refreshToken }) => {
    const current = sessionRef.current
    if (!current) return
    const next = { ...current, accessToken, refreshToken }
    persistSession(next)
    setSession(next)
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

  // On load, rehydrate a remembered session by minting a fresh access token.
  useEffect(() => {
    if (session) return

    const persisted = readPersisted()
    // `loading` is initialized false unless a remembered refresh token exists,
    // so there's nothing to settle here when one is absent.
    if (!persisted?.refreshToken) return

    let cancelled = false
    const rehydrate = async () => {
      try {
        const accessToken = await authService.refresh_access_token(
          persisted.refreshToken,
        )
        if (cancelled) return

        // Refresh the profile so a remembered session doesn't show stale data.
        // Non-fatal: fall back to the persisted user if the lookup fails.
        let user = persisted.user
        let profile = null
        try {
          profile = await userService.get_user(accessToken)
          user = withProfile(persisted.user, profile)
        } catch {
          // Keep the persisted user — backend unreachable or no profile.
        }
        if (cancelled) return

        setSession({
          accessToken,
          refreshToken: persisted.refreshToken,
          user,
          remembered: true,
        })
        // Refresh the cached avatar if the backend's image differs from cache.
        void syncProfileImageCache(user.id, profile?.userProfileImageUrl)
      } catch {
        // Refresh token expired/invalid — drop the remembered session.
        if (!cancelled) {
          localStorage.removeItem(STORAGE_KEY)
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
  // { id, username, email, creationDate, accessToken, refreshToken }.
  //
  // `profile` is the optional GetUserRestResponse from the app-backend (GET
  // /user). When present its fields are merged into the stored user so consumers
  // (e.g. the sidebar) get fullName / profileImageUrl without a second lookup.
  const login = useCallback((payload, rememberMe = false, profile = null) => {
    const next = {
      accessToken: payload.accessToken,
      refreshToken: payload.refreshToken,
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
    localStorage.removeItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_KEY)
    refreshPromiseRef.current = null
    setSession(null)
  }, [])

  // Mint a fresh access token from the current refresh token, persist it to the
  // active storage, and return it. Concurrent calls share one in-flight request.
  const refreshSession = useCallback(async () => {
    if (refreshPromiseRef.current) return refreshPromiseRef.current

    const run = (async () => {
      const current = sessionRef.current
      if (!current?.refreshToken) {
        throw new Error('No refresh token available')
      }
      const accessToken = await authService.refresh_access_token(
        current.refreshToken,
      )
      const next = { ...current, accessToken }
      persistSession(next)
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

  const value = useMemo(
    () => ({
      accessToken: session?.accessToken ?? null,
      refreshToken: session?.refreshToken ?? null,
      user: session?.user ?? null,
      isAuthenticated: Boolean(
        session?.accessToken && session?.refreshToken && session?.user,
      ),
      loading,
      login,
      logout,
      updateUser,
      applyTokens,
      refreshSession,
    }),
    [session, loading, login, logout, updateUser, applyTokens, refreshSession],
  )

  // Mirror the auth state into the Redux DevTools extension (dev only). Tokens
  // are reduced to presence flags to keep the inspector readable and avoid
  // dumping raw credentials into its timeline.
  useReduxDevtools('auth', {
    isAuthenticated: value.isAuthenticated,
    loading,
    user: value.user,
    remembered: session?.remembered ?? false,
    hasAccessToken: Boolean(session?.accessToken),
    hasRefreshToken: Boolean(session?.refreshToken),
  })

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export { AuthContext }
export default AuthProvider
