import { Navigate, Outlet } from 'react-router-dom'
import useAuth from '../hooks/useAuth'

export default function RequireAuth() {
  const { isAuthenticated, isInitializing } = useAuth()
  if (isInitializing) {
    return <p role="status">Verificando sesión...</p>
  }
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />
}
