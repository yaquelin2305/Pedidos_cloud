import { Outlet } from 'react-router-dom'
import useAuth from '../../modules/auth/hooks/useAuth'
import Header from './Header'
import Sidebar from './Sidebar'

export default function Layout() {
  const { roles, user } = useAuth()
  return (
    <>
      <a className="skip-link" href="#main-content">Saltar al contenido</a>
      <Header user={user} />
      <div className="app-body">
        <Sidebar />
        <main id="main-content" className="main-content" tabIndex={-1}>
          <div className="content-width" key={`${user?.id}-${roles.join(',')}`}><Outlet /></div>
        </main>
      </div>
    </>
  )
}
