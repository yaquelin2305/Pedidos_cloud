import { NavLink } from 'react-router-dom'
import useRoles from '../../modules/auth/hooks/useRoles'

export default function Sidebar() {
  const { hasAnyRole } = useRoles()
  return (
    <nav className="sidebar" aria-label="Navegación principal">
      <NavLink to="/dashboard">Panel<span aria-hidden="true">&rarr;</span></NavLink>
      <NavLink to="/orders">Pedidos<span aria-hidden="true">&rarr;</span></NavLink>
      {hasAnyRole(['Admin', 'Operator']) && <NavLink to="/catalog">Catálogo<span aria-hidden="true">&rarr;</span></NavLink>}
    </nav>
  )
}
