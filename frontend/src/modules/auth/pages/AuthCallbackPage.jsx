import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import '../styles/auth.css'

export default function AuthCallbackPage() {
  const navigate = useNavigate()
  useEffect(() => { const timer = setTimeout(() => navigate('/dashboard', { replace: true }), 250); return () => clearTimeout(timer) }, [navigate])
  return <main className="auth-page"><p role="status">Procesando inicio de sesión...</p></main>
}
