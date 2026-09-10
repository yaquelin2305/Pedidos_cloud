import Button from '../../../shared/components/Button'
import DataTable from '../../../shared/components/DataTable'
import { formatDate, formatMoney } from '../../../shared/utils/format'
import OrderStatusBadge from './OrderStatusBadge'
import OrderStatusActions from './OrderStatusActions'
import Avatar from '../../../shared/components/Avatar'

export default function OrderList({ orders, roles, onSelect, onChange, pending }) {
  // El servicio devuelve solo pedidos propios cuando el rol es Customer.
  const isOwner = roles.includes('Customer')
  const columns = [
    { key: 'id', label: 'Pedido', render: (order) => <Button variant="secondary" className="order-id" onClick={() => onSelect(order.id)} aria-label={`Ver pedido ${order.id}`}>{order.id}</Button> },
    { key: 'customerName', label: 'Cliente', render: (order) => <span className="person-cell"><Avatar name={order.customerName} /><span>{order.customerName}</span></span> },
    { key: 'items', label: 'Ítems', render: (order) => order.items.length },
    { key: 'total', label: 'Total', render: (order) => <span className="nowrap">{formatMoney(order.total)}</span> },
    { key: 'status', label: 'Estado', render: (order) => <OrderStatusBadge status={order.status} /> },
    { key: 'createdAt', label: 'Fecha', render: (order) => <time className="nowrap" dateTime={order.createdAt}>{formatDate(order.createdAt)}</time> },
    { key: 'actions', label: 'Acciones', render: (order) => <OrderStatusActions order={order} roles={roles} isOwner={isOwner} onChange={onChange} pending={pending} /> },
  ]
  return <DataTable caption="Listado de pedidos" columns={columns} rows={orders} />
}
