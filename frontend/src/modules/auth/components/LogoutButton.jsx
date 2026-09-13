import Button from '../../../shared/components/Button'
import useAuth from '../hooks/useAuth'

export default function LogoutButton() {
  const { logout } = useAuth()
  return <Button variant="secondary" onClick={logout}>Cerrar sesión</Button>
}
