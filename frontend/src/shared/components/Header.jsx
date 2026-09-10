import { Link } from 'react-router-dom'
import RoleSelector from '../../modules/auth/components/RoleSelector'
import LogoutButton from '../../modules/auth/components/LogoutButton'
import Avatar from './Avatar'

export default function Header({ user }) {
  return (
    <header className="app-header">
      <Link className="brand" to="/dashboard" aria-label="Pedidos360, inicio">
        <img src="/brand-mark.svg" width="32" height="32" alt="" />
        <span>Pedidos<span className="brand-number">360</span></span>
      </Link>
      <div className="header-actions">
        <RoleSelector />
        <div className="header-identity"><Avatar name={user.name} /><span>{user.name}</span></div>
        <LogoutButton />
      </div>
    </header>
  )
}
