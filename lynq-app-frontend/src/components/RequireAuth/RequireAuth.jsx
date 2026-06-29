import { Navigate } from 'react-router-dom'
import useAuth from '../../hooks/useAuth'

// Route guard: renders its children only for an authenticated session
// (accessToken + refreshToken + user all set). Otherwise redirects to the
// login page, replacing the history entry so back doesn't return here.
const RequireAuth = ({ children }) => {
  const { isAuthenticated, loading } = useAuth()

  // Wait for the remembered-session rehydration to settle before deciding,
  // otherwise we'd redirect a valid user mid-bootstrap.
  if (loading) {
    return null
  }

  if (!isAuthenticated) {
    return <Navigate to="/" replace />
  }

  return children
}

export default RequireAuth
