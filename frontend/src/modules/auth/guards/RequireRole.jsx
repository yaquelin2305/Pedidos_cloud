import { Navigate, Outlet } from 'react-router-dom'
import useRoles from '../hooks/useRoles'

export default function RequireRole({ allowedRoles }) {
  const { hasAnyRole } = useRoles()
  return hasAnyRole(allowedRoles) ? <Outlet /> : <Navigate to="/forbidden" replace />
}
