import { Link } from 'react-router-dom'
import useRoles from '../../auth/hooks/useRoles'

export default function SectionLinks() {
  const { hasAnyRole } = useRoles()
  return (
    <section className="section-links" aria-labelledby="sections-title">
      <h2 id="sections-title">Secciones</h2>
      <div className="section-link-list">
        <Link to="/orders"><span className="section-initial" aria-hidden="true">P</span><span>Pedidos</span><span className="section-arrow" aria-hidden="true">&rarr;</span></Link>
        {hasAnyRole(['Admin', 'Operator']) && <Link to="/catalog"><span className="section-initial section-initial--catalog" aria-hidden="true">C</span><span>Catálogo</span><span className="section-arrow" aria-hidden="true">&rarr;</span></Link>}
      </div>
    </section>
  )
}
