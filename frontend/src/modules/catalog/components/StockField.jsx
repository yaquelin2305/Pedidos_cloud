import { useState } from 'react'
import Button from '../../../shared/components/Button'

export default function StockField({ product, onSave, pending }) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(String(product.stock))
  const [error, setError] = useState('')
  const id = `stock-${product.id}`
  async function save(event) {
    event.preventDefault()
    if (pending) return
    if (value === '' || !Number.isSafeInteger(Number(value)) || Number(value) < 0) {
      setError('Ingresa un entero igual o mayor que cero.')
      return
    }
    setError('')
    try { await onSave(product.id, Number(value)); setEditing(false) }
    catch (failure) { setError(failure.message) }
  }
  if (!editing) return <div className="stock-display"><span className={product.stock === 0 ? 'error-text' : ''}>{product.stock === 0 ? 'Sin stock' : product.stock}</span><Button variant="secondary" disabled={pending} aria-label={`Ajustar stock de ${product.name}`} onClick={() => { setValue(String(product.stock)); setEditing(true) }}>Ajustar</Button></div>
  return (
    <form className="stock-form" onSubmit={save} noValidate>
      <label className="sr-only" htmlFor={id}>Stock de {product.name}</label>
      <input id={id} type="number" min="0" step="1" value={value} onChange={(event) => setValue(event.target.value)} disabled={pending} autoFocus aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-error` : undefined} />
      <div className="actions"><Button type="submit" disabled={pending}>Guardar</Button><Button variant="secondary" onClick={() => { setEditing(false); setError('') }} disabled={pending}>Cancelar</Button></div>
      {error && <p id={`${id}-error`} className="field-error" role="alert">{error}</p>}
    </form>
  )
}
