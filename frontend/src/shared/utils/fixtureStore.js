const products = [
  { id: 'P-001', name: 'Cuaderno universitario', description: '100 hojas, cuadriculado, tapa dura', price: 2490, stock: 42 },
  { id: 'P-002', name: 'Lápiz pasta azul', description: 'Punta media de 1 mm', price: 590, stock: 120 },
  { id: 'P-003', name: 'Resma carta', description: '500 hojas, papel blanco de 75 g', price: 4990, stock: 18 },
  { id: 'P-004', name: 'Carpeta con elástico', description: 'Formato oficio, color verde', price: 1790, stock: 0 },
  { id: 'P-005', name: 'Destacadores pastel', description: 'Estuche de 6 colores', price: 3990, stock: 25 },
  { id: 'P-006', name: 'Archivador oficio', description: 'Lomo ancho con palanca metálica', price: 3490, stock: 9 },
]
const names = ['Camila Torres', 'Diego Rojas', 'Valentina Soto', 'Camila Torres', 'Martin Perez', 'Sofia Silva', 'Camila Torres', 'Diego Rojas']
const statuses = ['CREATED', 'ACCEPTED', 'PREPARING', 'DISPATCHED', 'DELIVERED', 'CANCELLED', 'CREATED', 'CREATED']
const orders = names.map((customerName, index) => {
  const product = products[index % products.length]
  const quantity = index % 3 + 1
  return {
    id: `ORD-${1001 + index}`, customerName, status: statuses[index],
    createdAt: `2026-09-${String(10 - index).padStart(2, '0')}T12:00:00.000Z`,
    total: product.price * quantity,
    items: [{ productId: product.id, productName: product.name, quantity, unitPrice: product.price }],
  }
})

// Los dueños son metadatos de fixtures; no forman parte del DTO del pedido.
export const fixtureStore = {
  products, orders,
  owners: Object.fromEntries(orders.map((order, index) => [order.id, names[index] === 'Camila Torres' ? 'customer-1' : `customer-${index + 2}`])),
}

export async function fixtureRequest(operation) {
  await new Promise((resolve) => setTimeout(resolve, 300))
  return structuredClone(operation())
}

export function fixtureList(module, rows) {
  if (import.meta.env?.DEV) {
    let scenario
    try { scenario = JSON.parse(sessionStorage.getItem('pedidos360.scenarios'))?.[module] } catch { scenario = null }
    if (scenario === 'error') throw new Error('No se pudieron cargar los datos. Vuelve a intentarlo.')
    if (scenario === 'empty') return []
  }
  return rows
}
