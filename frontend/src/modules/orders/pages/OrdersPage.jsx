import { useState } from 'react'
import useAuth from '../../auth/hooks/useAuth'
import useOrders from '../hooks/useOrders'
import OrderList from '../components/OrderList'
import OrderDetail from '../components/OrderDetail'
import OrderForm from '../components/OrderForm'
import OrderStatusConfirmation from '../components/OrderStatusConfirmation'
import { orderStates } from '../models/order'
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
  const [statusChange, setStatusChange] = useState(null)
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
    setNotice('')
    try {
      await changeStatus(order.id, status)
      setStatusChange(null)
      setRevision((value) => value + 1)
      setNotice(`Pedido ${order.id} actualizado a ${orderStates[status].toLowerCase()}.`)
    } catch (failure) { setActionError(failure.message) }
  }
  function requestChange(order, status) {
    setStatusChange({ order, status })
    setActionError('')
    setNotice('')
  }
  return (
    <>
      <PageHeader title="Pedidos"><Button onClick={() => { setCreating(true); setSelectedId(null); setStatusChange(null); setActionError('') }} disabled={pending || !!statusChange}>Nuevo pedido</Button></PageHeader>
      <p role="status">{notice}</p>
      {creating && <OrderForm onSave={save} onCancel={() => setCreating(false)} pending={pending} />}
      {statusChange && <OrderStatusConfirmation order={statusChange.order} status={statusChange.status} onConfirm={() => transition(statusChange.order, statusChange.status)} onDismiss={() => { setStatusChange(null); setActionError('') }} pending={pending} />}
      {actionError && <StatusMessage type="error" message={actionError} />}
      {selectedId && <OrderDetail key={selectedId} id={selectedId} revision={revision} onClose={() => setSelectedId(null)} />}
      {loading ? <StatusMessage type="loading" message="Cargando pedidos..." /> : error ? <StatusMessage type="error" message={error} onRetry={reload} /> : orders.length === 0 ? <StatusMessage message="No hay pedidos registrados." /> : <OrderList orders={orders} roles={roles} onSelect={(id) => { setSelectedId(id); setCreating(false) }} onChange={requestChange} pending={pending || !!statusChange} />}
    </>
  )
}
