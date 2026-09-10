import { orderStates } from '../models/order'

export default function OrderStatusBadge({ status }) {
  return <span className={`order-badge order-badge--${status.toLowerCase()}`}>{orderStates[status] ?? status}</span>
}
