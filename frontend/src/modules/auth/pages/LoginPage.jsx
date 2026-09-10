import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import LoginButton from '../components/LoginButton'
import RegisterButton from '../components/RegisterButton'
import '../styles/auth.css'

export default function LoginPage() {
  const { isAuthenticated, login, register } = useAuth()
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  if (isAuthenticated) return <Navigate to="/dashboard" replace />
  async function enter(action) {
    setPending(true)
    setError('')
    try { await action(); navigate('/auth/callback', { replace: true }) }
    catch { setError('No se pudo iniciar sesión. Vuelve a intentarlo.'); setPending(false) }
  }
  return (
    <main className="auth-page">
      <div className="auth-content">
        <img className="brand-mark" src="/brand-mark.svg" alt="" width="56" height="56" />
        <h1>Pedidos<span className="brand-number">360</span></h1>
        <p>Gestión de pedidos y catálogo</p>
        <div className="auth-actions">
          <LoginButton onClick={() => enter(login)} disabled={pending} />
          <RegisterButton onClick={() => enter(register)} disabled={pending} />
        </div>
        <p className="auth-error" role="alert">{error}</p>
      </div>
    </main>
  )
}
