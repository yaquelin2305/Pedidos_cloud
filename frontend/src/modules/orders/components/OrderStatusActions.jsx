import Button from '../../../shared/components/Button'
import { getOrderActions } from '../models/order'

export default function OrderStatusActions({ order, roles, isOwner, onChange, pending }) {
  const actions = getOrderActions(order.status, roles, isOwner)
  if (!actions.length) return <span className="text-muted">Sin acciones</span>
  return <div className="actions">{actions.map((action) => <Button key={action.status} variant={action.status === 'CANCELLED' ? 'danger' : 'secondary'} onClick={() => onChange(order, action.status)} disabled={pending}>{action.label}</Button>)}</div>
}
