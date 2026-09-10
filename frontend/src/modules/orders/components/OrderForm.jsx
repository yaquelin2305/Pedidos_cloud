import { useRef, useState } from 'react'
import Button from '../../../shared/components/Button'
import Field from '../../../shared/components/Field'
import StatusMessage from '../../../shared/components/StatusMessage'
import { formatMoney } from '../../../shared/utils/format'
import useAuth from '../../auth/hooks/useAuth'
import useOrderProducts from '../hooks/useOrderProducts'
import { validateOrder } from '../models/order'

export default function OrderForm({ onSave, onCancel, pending }) {
  const { user, roles } = useAuth()
  const { products, loading, error, reload } = useOrderProducts()
  const [customerName, setCustomerName] = useState(user.name)
  const [items, setItems] = useState([{ key: 0, productId: '', quantity: '1' }])
  const nextKey = useRef(1)
  const form = useRef(null)
  const [errors, setErrors] = useState({})
  const [saveError, setSaveError] = useState('')
  const isCustomer = roles.includes('Customer')
  const total = items.reduce((sum, item) => sum + (products.find((product) => product.id === item.productId)?.price ?? 0) * (Number(item.quantity) || 0), 0)
  function changeItem(key, field, value) {
    setItems((previous) => previous.map((item) => item.key === key ? { ...item, [field]: value } : item))
    setErrors((previous) => ({ ...previous, items: '' }))
  }
  async function submit(event) {
    event.preventDefault()
    if (pending || loading || error) return
    const input = { customerName, items: items.map(({ productId, quantity }) => ({ productId, quantity: Number(quantity) })) }
    const nextErrors = validateOrder(input)
    setErrors(nextErrors)
    setSaveError('')
    if (Object.keys(nextErrors).length) {
      form.current.querySelector(nextErrors.customerName ? '#order-customer' : 'select')?.focus()
      return
    }
    try { await onSave(input) } catch (failure) { setSaveError(failure.message) }
  }
  return (
    <section className="form-section" aria-labelledby="order-form-title">
      <h2 id="order-form-title">Nuevo pedido</h2>
      {loading ? <StatusMessage type="loading" message="Cargando productos..." /> : error ? <StatusMessage type="error" message={error} onRetry={reload} /> : products.length === 0 ? <StatusMessage message="No hay productos disponibles para crear un pedido." /> :
        <form ref={form} className="form-stack" onSubmit={submit} noValidate aria-busy={pending}>
          <Field id="order-customer" label="Cliente" error={errors.customerName}>
            <input id="order-customer" value={customerName} onChange={(event) => setCustomerName(event.target.value)} readOnly={isCustomer} disabled={pending} autoFocus required aria-invalid={Boolean(errors.customerName)} aria-describedby={errors.customerName ? 'order-customer-error' : undefined} />
          </Field>
          <fieldset className="order-items" aria-describedby={errors.items ? 'order-items-error' : undefined}>
            <legend>Productos</legend>
            {items.map((item, index) => <div className="order-item" key={item.key}>
              <Field id={`order-product-${item.key}`} label={`Producto ${index + 1}`}>
                <select id={`order-product-${item.key}`} value={item.productId} onChange={(event) => changeItem(item.key, 'productId', event.target.value)} disabled={pending} required aria-invalid={Boolean(errors.items)} aria-describedby={errors.items ? 'order-items-error' : undefined}>
                  <option value="">Seleccionar producto</option>
                  {products.map((product) => <option key={product.id} value={product.id} disabled={items.some((other) => other.key !== item.key && other.productId === product.id)}>{product.name} - {formatMoney(product.price)}{product.stock === 0 ? ' (sin stock)' : ''}</option>)}
                </select>
              </Field>
              <div className="item-quantity">
                <Field id={`order-quantity-${item.key}`} label="Cantidad"><input id={`order-quantity-${item.key}`} type="number" min="1" step="1" value={item.quantity} onChange={(event) => changeItem(item.key, 'quantity', event.target.value)} disabled={pending} required aria-invalid={Boolean(errors.items)} aria-describedby={errors.items ? 'order-items-error' : undefined} /></Field>
                <Button variant="secondary" onClick={() => setItems((previous) => previous.filter((row) => row.key !== item.key))} disabled={pending || items.length === 1} aria-label={`Quitar producto ${index + 1}`}>Quitar</Button>
              </div>
            </div>)}
            {errors.items && <p id="order-items-error" className="field-error" role="alert">{errors.items}</p>}
            <Button variant="secondary" onClick={() => setItems((previous) => [...previous, { key: nextKey.current++, productId: '', quantity: '1' }])} disabled={pending || items.length >= products.length}>Agregar producto</Button>
          </fieldset>
          <p className="order-total" aria-live="polite">Total <strong>{formatMoney(Number.isFinite(total) ? total : 0)}</strong></p>
          {saveError && <p className="error-text" role="alert">{saveError}</p>}
          <div className="actions"><Button type="submit" disabled={pending}>{pending ? 'Creando...' : 'Crear pedido'}</Button><Button variant="secondary" onClick={onCancel} disabled={pending}>Cancelar</Button></div>
        </form>}
      {(loading || error || !products.length) && <Button variant="secondary" onClick={onCancel}>Cancelar</Button>}
    </section>
  )
}
