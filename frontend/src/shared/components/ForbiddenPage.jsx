import { Link } from 'react-router-dom'

export default function ForbiddenPage() {
  return <main className="standalone-page"><h1>Sin permisos</h1><p>No tienes acceso a esta sección con tu rol actual.</p><Link to="/dashboard">Volver al panel</Link></main>
}
