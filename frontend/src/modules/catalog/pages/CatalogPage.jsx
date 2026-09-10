import { useState } from 'react'
import useRoles from '../../auth/hooks/useRoles'
import useProducts from '../hooks/useProducts'
import ProductList from '../components/ProductList'
import ProductForm from '../components/ProductForm'
import Button from '../../../shared/components/Button'
import PageHeader from '../../../shared/components/PageHeader'
import StatusMessage from '../../../shared/components/StatusMessage'
import '../styles/catalog.css'

export default function CatalogPage() {
  const { hasRole } = useRoles()
  const { products, loading, error, pending, reload, create, update, adjustStock, remove } = useProducts()
  const [form, setForm] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [deleteError, setDeleteError] = useState('')
  const [notice, setNotice] = useState('')
  const canManage = hasRole('Admin')
  async function save(values) {
    if (form.product) await update(form.product.id, values)
    else await create(values)
    setForm(null)
    setNotice('Producto guardado.')
  }
  async function confirmDelete() {
    setDeleteError('')
    try { await remove(deleting.id); setDeleting(null); setNotice('Producto eliminado.') }
    catch (failure) { setDeleteError(failure.message) }
  }
  async function stock(id, value) {
    await adjustStock(id, value)
    setNotice('Stock actualizado.')
  }
  return (
    <>
      <PageHeader title="Catálogo">{canManage && <Button onClick={() => { setForm({ product: null }); setDeleting(null); setNotice('') }} disabled={pending}>Nuevo producto</Button>}</PageHeader>
      <p className="sr-only" role="status">{notice}</p>
      {form && canManage && <ProductForm key={form.product?.id ?? 'new'} product={form.product} onSave={save} onCancel={() => setForm(null)} pending={pending} />}
      {deleting && canManage && <section className="confirmation" aria-label="Confirmar eliminación"><p>Eliminar <strong>{deleting.name}</strong>?</p><Button variant="danger" onClick={confirmDelete} disabled={pending}>{pending ? 'Eliminando...' : 'Confirmar eliminación'}</Button><Button variant="secondary" onClick={() => setDeleting(null)} disabled={pending}>Conservar producto</Button>{deleteError && <p className="error-text" role="alert">{deleteError}</p>}</section>}
      {loading ? <StatusMessage type="loading" message="Cargando catálogo..." /> : error ? <StatusMessage type="error" message={error} onRetry={reload} /> : products.length === 0 ? <StatusMessage message="No hay productos en el catálogo." /> : <ProductList products={products} canManage={canManage} onEdit={(product) => { setForm({ product }); setDeleting(null); setNotice('') }} onDelete={(product) => { setDeleting(product); setForm(null); setDeleteError('') }} onStock={stock} pending={pending} />}
    </>
  )
}
