import { useState } from 'react'
import useAuth from '../../auth/hooks/useAuth'
import useOrders from '../hooks/useOrders'
import OrderList from '../components/OrderList'
import OrderDetail from '../components/OrderDetail'
import OrderForm from '../components/OrderForm'
import Button from '../../../shared/components/Button'
import PageHeader from '../../../shared/components/PageHeader'
import StatusMessage from '../../../shared/components/StatusMessage'
import '../styles/orders.css'

export default function OrdersPage() {
  const { roles } = useAuth()
  const { orders, loading, error, pending, reload, create, changeStatus } = useOrders()
  const [creating, setCreating] = useState(false)
  const [selectedId, setSelectedId] = useState(null)
  const [revision, setRevision] = useState(0)
  const [cancelling, setCancelling] = useState(null)
  const [actionError, setActionError] = useState('')
  const [notice, setNotice] = useState('')
  async function save(input) {
    const order = await create(input)
    setCreating(false)
    setSelectedId(order.id)
    setNotice('Pedido creado.')
  }
  async function transition(order, status) {
    setActionError('')
    try {
      await changeStatus(order.id, status)
      setCancelling(null)
      setRevision((value) => value + 1)
      setNotice('Estado del pedido actualizado.')
    } catch (failure) { setActionError(failure.message) }
  }
  function requestChange(order, status) {
    if (status === 'CANCELLED') { setCancelling(order); setActionError('') }
    else transition(order, status)
  }
  return (
    <>
      <PageHeader title="Pedidos"><Button onClick={() => { setCreating(true); setSelectedId(null); setCancelling(null); setActionError('') }} disabled={pending}>Nuevo pedido</Button></PageHeader>
      <p className="sr-only" role="status">{notice}</p>
      {creating && <OrderForm onSave={save} onCancel={() => setCreating(false)} pending={pending} />}
      {cancelling && <section className="confirmation" aria-label="Confirmar cancelación"><p>Cancelar el pedido <strong>{cancelling.id}</strong>?</p><Button variant="danger" onClick={() => transition(cancelling, 'CANCELLED')} disabled={pending}>Confirmar cancelación</Button><Button variant="secondary" onClick={() => setCancelling(null)} disabled={pending}>Conservar pedido</Button></section>}
      {actionError && <StatusMessage type="error" message={actionError} />}
      {selectedId && <OrderDetail key={selectedId} id={selectedId} revision={revision} onClose={() => setSelectedId(null)} />}
      {loading ? <StatusMessage type="loading" message="Cargando pedidos..." /> : error ? <StatusMessage type="error" message={error} onRetry={reload} /> : orders.length === 0 ? <StatusMessage message="No hay pedidos registrados." /> : <OrderList orders={orders} roles={roles} onSelect={(id) => { setSelectedId(id); setCreating(false) }} onChange={requestChange} pending={pending} />}
    </>
  )
}
