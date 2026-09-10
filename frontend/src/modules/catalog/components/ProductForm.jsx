import { useRef, useState } from 'react'
import Button from '../../../shared/components/Button'
import Field from '../../../shared/components/Field'
import { validateProduct } from '../models/product'

export default function ProductForm({ product, onSave, onCancel, pending }) {
  const [values, setValues] = useState(product ?? { name: '', description: '', price: '', stock: '0' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const form = useRef(null)
  function change(event) {
    const { name, value } = event.target
    setValues((previous) => ({ ...previous, [name]: value }))
    setErrors((previous) => ({ ...previous, [name]: '' }))
  }
  async function submit(event) {
    event.preventDefault()
    if (pending) return
    const nextErrors = validateProduct(values)
    setErrors(nextErrors)
    setError('')
    if (Object.keys(nextErrors).length) { form.current.elements.namedItem(Object.keys(nextErrors)[0])?.focus(); return }
    try { await onSave(values) } catch (failure) { setError(failure.message) }
  }
  const fields = [
    { name: 'name', label: 'Nombre', type: 'text' },
    { name: 'price', label: 'Precio ($)', type: 'number', min: '0', step: '0.01' },
    { name: 'stock', label: 'Stock', type: 'number', min: '0', step: '1' },
  ]
  return (
    <section className="form-section" aria-labelledby="product-form-title">
      <h2 id="product-form-title">{product ? 'Editar producto' : 'Nuevo producto'}</h2>
      <form ref={form} className="form-stack" onSubmit={submit} noValidate aria-busy={pending}>
        {fields.map(({ name, label, ...input }) => (
          <Field key={name} id={`product-${name}`} label={label} error={errors[name]}>
            <input {...input} id={`product-${name}`} name={name} value={values[name]} onChange={change} disabled={pending} autoFocus={name === 'name'} required aria-invalid={Boolean(errors[name])} aria-describedby={errors[name] ? `product-${name}-error` : undefined} />
          </Field>
        ))}
        <Field id="product-description" label="Descripción">
          <textarea id="product-description" name="description" value={values.description} onChange={change} disabled={pending} />
        </Field>
        {error && <p className="error-text" role="alert">{error}</p>}
        <div className="actions">
          <Button type="submit" disabled={pending}>{pending ? 'Guardando...' : 'Guardar producto'}</Button>
          <Button variant="secondary" onClick={onCancel} disabled={pending}>Cancelar</Button>
        </div>
      </form>
    </section>
  )
}
