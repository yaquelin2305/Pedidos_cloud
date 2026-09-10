import DataTable from '../../../shared/components/DataTable'
import Button from '../../../shared/components/Button'
import { formatMoney } from '../../../shared/utils/format'
import StockField from './StockField'

export default function ProductList({ products, canManage, onEdit, onDelete, onStock, pending }) {
  const columns = [
    { key: 'name', label: 'Nombre', render: (product) => <span className="product-name">{product.name}</span> },
    { key: 'description', label: 'Descripción', render: (product) => <span className="product-description">{product.description || 'Sin descripción'}</span> },
    { key: 'price', label: 'Precio', render: (product) => <span className="nowrap">{formatMoney(product.price)}</span> },
    { key: 'stock', label: 'Stock', render: (product) => <StockField product={product} onSave={onStock} pending={pending} /> },
  ]
  if (canManage) columns.push({ key: 'actions', label: 'Acciones', render: (product) => <div className="actions"><Button variant="secondary" onClick={() => onEdit(product)} disabled={pending}>Editar</Button><Button variant="danger" onClick={() => onDelete(product)} disabled={pending}>Eliminar</Button></div> })
  return <DataTable caption="Productos del catálogo" columns={columns} rows={products} />
}
