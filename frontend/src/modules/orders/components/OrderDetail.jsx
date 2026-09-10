import { useEffect, useRef } from 'react'
import Button from '../../../shared/components/Button'
import DataTable from '../../../shared/components/DataTable'
import StatusMessage from '../../../shared/components/StatusMessage'
import { formatDate, formatMoney } from '../../../shared/utils/format'
import useOrderDetail from '../hooks/useOrderDetail'
import OrderStatusBadge from './OrderStatusBadge'

export default function OrderDetail({ id, revision, onClose }) {
  const { order, loading, error, reload } = useOrderDetail(id, revision)
  const title = useRef(null)
  useEffect(() => { title.current?.focus() }, [id])
  const columns = [
    { key: 'productName', label: 'Producto' },
    { key: 'quantity', label: 'Cantidad' },
    { key: 'unitPrice', label: 'Precio unitario', render: (item) => formatMoney(item.unitPrice) },
    { key: 'subtotal', label: 'Subtotal', render: (item) => formatMoney(item.quantity * item.unitPrice) },
  ]
  return (
    <section className="order-detail" aria-labelledby="order-detail-title">
      <div className="page-header"><h2 id="order-detail-title" ref={title} tabIndex={-1}>Pedido {id}</h2><Button variant="secondary" onClick={onClose}>Cerrar detalle</Button></div>
      {loading ? <StatusMessage type="loading" message="Cargando pedido..." /> : error ? <StatusMessage type="error" message={error} onRetry={reload} /> : order && <>
        <div className="order-summary"><p><strong>{order.customerName}</strong></p><OrderStatusBadge status={order.status} /><time dateTime={order.createdAt}>{formatDate(order.createdAt)}</time></div>
        <DataTable caption={`Ítems del pedido ${id}`} columns={columns} rows={order.items} rowKey="productId" />
        <p className="order-total">Total <strong>{formatMoney(order.total)}</strong></p>
      </>}
    </section>
  )
}
