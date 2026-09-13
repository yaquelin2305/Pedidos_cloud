import apiClient from '../../../shared/http/apiClient'
import { getHttpErrorMessage } from '../../../shared/utils/httpError'
import { validateOrder } from '../models/order'

// El backend guarda los estados en español; las vistas y el modelo de transiciones usan
// ingles. Esta es la unica frontera donde se traduce en los dos sentidos.
const STATUS_TO_BACKEND = {
  CREATED: 'CREADO',
  ACCEPTED: 'ACEPTADO',
  PREPARING: 'EN_PREPARACION',
  DISPATCHED: 'DESPACHADO',
  DELIVERED: 'ENTREGADO',
  CANCELLED: 'CANCELADO',
}
const STATUS_FROM_BACKEND = Object.fromEntries(
  Object.entries(STATUS_TO_BACKEND).map(([front, back]) => [back, front]),
)

function fromBackendOrder(order) {
  return {
    id: order.id,
    customerName: order.customerName,
    status: STATUS_FROM_BACKEND[order.status] ?? order.status,
    createdAt: order.createdAt,
    total: order.totalAmount,
    items: order.items.map((item) => ({
      productId: item.productId,
      productName: item.productName,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      subtotal: item.subtotal,
    })),
  }
}

function fromBackendProductSummary(product) {
  return { id: product.id, name: product.name, price: product.price, stock: product.stock }
}

async function unwrap(promise) {
  try {
    const { data } = await promise
    return data
  } catch (error) {
    throw new Error(getHttpErrorMessage(error))
  }
}

export const ordersService = {
  list: async () => (await unwrap(apiClient.get('/api/orders'))).map(fromBackendOrder),

  getById: async (id) => fromBackendOrder(await unwrap(apiClient.get(`/api/orders/${id}`))),

  // Comparte /api/catalog/products con el catalogo: los tres roles pueden consultarlo para
  // armar el selector del formulario, aunque solo Admin/Operator vean la pantalla de catalogo.
  listProducts: async () =>
    (await unwrap(apiClient.get('/api/catalog/products'))).map(fromBackendProductSummary),

  create: async (input) => {
    const errors = validateOrder(input)
    if (Object.keys(errors).length) throw new Error(Object.values(errors)[0])
    const body = {
      items: input.items.map(({ productId, quantity }) => ({
        productId: Number(productId),
        quantity: Number(quantity),
      })),
    }
    return fromBackendOrder(await unwrap(apiClient.post('/api/orders', body)))
  },

  // Cancelar es DELETE en el backend, no una transicion de estado como las demas: el modelo
  // de transiciones del front trata CANCELLED igual que el resto, asi que la diferencia se
  // resuelve aqui, no en los componentes que llaman a changeStatus.
  changeStatus: async (id, status) => {
    if (status === 'CANCELLED') {
      return fromBackendOrder(await unwrap(apiClient.delete(`/api/orders/${id}`)))
    }
    const body = { status: STATUS_TO_BACKEND[status] ?? status }
    return fromBackendOrder(await unwrap(apiClient.patch(`/api/orders/${id}/status`, body)))
  },
}
