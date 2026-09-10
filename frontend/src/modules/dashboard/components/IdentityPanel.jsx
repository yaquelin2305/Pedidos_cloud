import useAuth from '../../auth/hooks/useAuth'

export default function IdentityPanel() {
  const { user, roles, scopes } = useAuth()
  return (
    <section className="identity-section" aria-labelledby="identity-title">
      <div className="identity-heading"><h2 id="identity-title">Mi sesión</h2></div>
      <dl className="identity-list">
        <div><dt>Nombre</dt><dd>{user.name}</dd></div>
        <div><dt>Usuario</dt><dd>{user.username}</dd></div>
        <div><dt>Roles</dt><dd><span className="identity-role">{roles.join(', ')}</span></dd></div>
        <div><dt>Scopes</dt><dd className="scope-list">{scopes.map((scope) => <code key={scope}>{scope}</code>)}</dd></div>
      </dl>
    </section>
  )
}
