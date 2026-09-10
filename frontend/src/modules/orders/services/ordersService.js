import { requireSession } from '../../auth/session/sessionStore'
import { fixtureList, fixtureRequest, fixtureStore } from '../../../shared/utils/fixtureStore'
import { getOrderActions, validateOrder } from '../models/order'

function findOrder(id, session) {
  const order = fixtureStore.orders.find((item) => item.id === id)
  if (!order) throw new Error('El pedido no existe.')
  if (session.role === 'Customer' && fixtureStore.owners[id] !== session.user.id) throw new Error('No tienes acceso a este pedido.')
  return order
}

export const ordersService = {
  list: () => fixtureRequest(() => {
    const session = requireSession()
    const orders = session.role === 'Customer' ? fixtureStore.orders.filter((order) => fixtureStore.owners[order.id] === session.user.id) : fixtureStore.orders
    return fixtureList('orders', orders)
  }),
  getById: (id) => fixtureRequest(() => findOrder(id, requireSession())),
  listProducts: () => fixtureRequest(() => { requireSession(); return fixtureList('products', fixtureStore.products) }),
  create: (input) => fixtureRequest(() => {
    const session = requireSession()
    const errors = validateOrder(input)
    if (Object.keys(errors).length) throw new Error(Object.values(errors)[0])
    const items = input.items.map((item) => {
      const product = fixtureStore.products.find((product) => product.id === item.productId)
      if (!product) throw new Error('Uno de los productos ya no está disponible.')
      return { productId: product.id, productName: product.name, quantity: Number(item.quantity), unitPrice: product.price }
    })
    const order = {
      id: `ORD-${crypto.randomUUID().slice(0, 8)}`,
      customerName: session.role === 'Customer' ? session.user.name : input.customerName.trim(),
      status: 'CREATED', createdAt: new Date().toISOString(), items,
      total: items.reduce((total, item) => total + item.quantity * item.unitPrice, 0),
    }
    if (!Number.isFinite(order.total)) throw new Error('El total del pedido excede el límite permitido.')
    fixtureStore.orders.unshift(order)
    fixtureStore.owners[order.id] = session.user.id
    return order
  }),
  changeStatus: (id, status) => fixtureRequest(() => {
    const session = requireSession()
    const order = findOrder(id, session)
    const actions = getOrderActions(order.status, [session.role], fixtureStore.owners[id] === session.user.id)
    if (!actions.some((action) => action.status === status)) throw new Error('El cambio de estado no está permitido.')
    if (status === 'ACCEPTED') {
      const allocations = order.items.map((item) => {
        const product = fixtureStore.products.find((product) => product.id === item.productId)
        if (!product || product.stock < item.quantity) throw new Error(`Stock insuficiente para ${item.productName}.`)
        return { product, quantity: item.quantity }
      })
      allocations.forEach(({ product, quantity }) => { product.stock -= quantity })
    }
    order.status = status
    return order
  }),
}
