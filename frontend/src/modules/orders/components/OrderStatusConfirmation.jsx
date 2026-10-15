import { useEffect, useRef } from 'react'
import Button from '../../../shared/components/Button'
import { isOrderCorrection, orderStates } from '../models/order'

export default function OrderStatusConfirmation({ order, status, onConfirm, onDismiss, pending }) {
  const title = useRef(null)
  const cancellation = status === 'CANCELLED'
  const correction = isOrderCorrection(order.status, status)
  useEffect(() => { title.current?.focus() }, [order.id, status])

  return (
    <section className="confirmation order-confirmation" aria-labelledby="order-confirmation-title" aria-busy={pending}>
      <h2 id="order-confirmation-title" ref={title} tabIndex={-1}>
        {cancellation ? 'Cancelar pedido' : correction ? 'Corregir estado del pedido' : 'Confirmar cambio de estado'}
      </h2>
      <p>Pedido <strong>{order.id}</strong>: <strong>{orderStates[order.status]}</strong> → <strong>{orderStates[status]}</strong>.</p>
      {correction && <p>Usa esta opción si el estado se registró por error. El pedido seguirá activo y el stock no cambiará.</p>}
      {status === 'ACCEPTED' && !correction && <p>Al aceptar se descontará el stock de los productos.</p>}
      {cancellation && <p>El pedido quedará cancelado y no podrá reactivarse. Esta acción no repone el stock descontado.</p>}
      <div className="actions">
        <Button variant="secondary" onClick={onDismiss} disabled={pending}>Mantener estado actual</Button>
        <Button variant={cancellation ? 'danger' : 'primary'} onClick={onConfirm} disabled={pending}>
          {pending ? 'Guardando...' : cancellation ? 'Confirmar cancelación' : correction ? 'Confirmar corrección' : 'Confirmar cambio'}
        </Button>
      </div>
    </section>
  )
}
