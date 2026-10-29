import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { api, setSessionLostHandler, tokens } from './api'
import type { SessionResponse, SessionUser } from './types'

interface AuthContextValue {
  user: SessionUser | null
  loading: boolean
  login: (email: string, password: string) => Promise<SessionUser>
  logout: () => Promise<void>
  /** Adopts a session created elsewhere, such as by activation. */
  adoptSession: (session: SessionResponse) => void
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null)
  const [loading, setLoading] = useState(true)

  // Restore the session on load: a stored token is only a claim, so it is
  // checked against the server before any interface is shown.
  useEffect(() => {
    let cancelled = false

    async function restore() {
      if (!tokens.access()) {
        setLoading(false)
        return
      }
      try {
        const session = await api.get<SessionUser>('/api/auth/session')
        if (!cancelled) {
          setUser(session)
        }
      } catch {
        tokens.clear()
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void restore()
    return () => {
      cancelled = true
    }
  }, [])

  // When a refresh finally fails, drop back to the login screen rather than
  // leaving the person staring at empty panels.
  useEffect(() => {
    setSessionLostHandler(() => setUser(null))
    return () => setSessionLostHandler(null)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const session = await api.anonymousPost<SessionResponse>('/api/auth/login', { email, password })
    tokens.set(session.accessToken, session.refreshToken)
    setUser(session.user)
    return session.user
  }, [])

  const adoptSession = useCallback((session: SessionResponse) => {
    tokens.set(session.accessToken, session.refreshToken)
    setUser(session.user)
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = tokens.refresh()
    try {
      if (refreshToken) {
        await api.post('/api/auth/logout', { refreshToken })
      }
    } catch {
      // Signing out locally must work even with no connection.
    } finally {
      tokens.clear()
      setUser(null)
    }
  }, [])

  const refreshUser = useCallback(async () => {
    if (!tokens.access()) {
      return
    }
    try {
      setUser(await api.get<SessionUser>('/api/auth/session'))
    } catch {
      /* keep the current user; the next request will surface any real problem */
    }
  }, [])

  const value = useMemo(
    () => ({ user, loading, login, logout, adoptSession, refreshUser }),
    [user, loading, login, logout, adoptSession, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}

/** Where each role lands after signing in. */
export function homePathFor(user: SessionUser): string {
  switch (user.role) {
    case 'ADMIN':
      return '/admin'
    case 'SUPERVISOR':
      return '/supervisor'
    default:
      return '/home'
  }
}
