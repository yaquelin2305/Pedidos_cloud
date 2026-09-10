import PageHeader from '../../../shared/components/PageHeader'
import IdentityPanel from '../components/IdentityPanel'
import SectionLinks from '../components/SectionLinks'
import '../styles/dashboard.css'

export default function DashboardPage() {
  return <><PageHeader title="Panel" /><IdentityPanel /><SectionLinks /></>
}
