import useAuth from '../hooks/useAuth'
import { availableRoles, sessionStore } from '../session/sessionStore'

export default function RoleSelector() {
  const { roles } = useAuth()
  return (
    <div className="role-selector">
      <label htmlFor="session-role">Rol</label>
      <select id="session-role" value={roles[0] ?? ''} onChange={(event) => sessionStore.setRole(event.target.value)}>
        {availableRoles.map((role) => <option key={role}>{role}</option>)}
      </select>
    </div>
  )
}
