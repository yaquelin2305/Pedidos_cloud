import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import '../styles/auth.css'

export default function AuthCallbackPage() {
  const { isAuthenticated, isInitializing } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (isInitializing) return
    navigate(isAuthenticated ? '/dashboard' : '/login', { replace: true })
  }, [isInitializing, isAuthenticated, navigate])

  return (
    <main className="auth-page">
      <p role="status">Procesando inicio de sesión...</p>
    </main>
  )
}
