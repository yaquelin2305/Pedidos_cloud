import { useNavigate } from 'react-router-dom'
import Button from '../../../shared/components/Button'
import useAuth from '../hooks/useAuth'

export default function LogoutButton() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  return <Button variant="secondary" onClick={() => { logout(); navigate('/login', { replace: true }) }}>Cerrar sesión</Button>
}
