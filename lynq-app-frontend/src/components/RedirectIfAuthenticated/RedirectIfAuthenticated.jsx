import { Navigate } from 'react-router-dom'
import useAuth from '../../hooks/useAuth'

// Inverse of RequireAuth: guards the public-only routes (login, register).
// An already-authenticated session has no business on those pages, so it is
// redirected to /home, replacing the history entry so back doesn't return here.
const RedirectIfAuthenticated = ({ children }) => {
  const { isAuthenticated, loading } = useAuth()

  // Wait for the remembered-session rehydration to settle before deciding,
  // otherwise we'd flash the login page to a valid user mid-bootstrap.
  if (loading) {
    return null
  }

  if (isAuthenticated) {
    return <Navigate to="/home" replace />
  }

  return children
}

export default RedirectIfAuthenticated
