import { requireSession } from '../../auth/session/sessionStore'
import { fixtureList, fixtureRequest, fixtureStore } from '../../../shared/utils/fixtureStore'
import { validateProduct } from '../models/product'

const managers = ['Admin', 'Operator']
function findProduct(id) {
  const product = fixtureStore.products.find((item) => item.id === id)
  if (!product) throw new Error('El producto no existe o fue eliminado.')
  return product
}
function validate(input) {
  const errors = validateProduct(input)
  if (Object.keys(errors).length) throw new Error(Object.values(errors)[0])
  return { name: input.name.trim(), description: input.description?.trim() ?? '', price: Number(input.price), stock: Number(input.stock) }
}

export const catalogService = {
  list: () => fixtureRequest(() => { requireSession(managers); return fixtureList('catalog', fixtureStore.products) }),
  getById: (id) => fixtureRequest(() => { requireSession(managers); return findProduct(id) }),
  create: (input) => fixtureRequest(() => {
    requireSession(['Admin'])
    const product = { id: `P-${crypto.randomUUID().slice(0, 8)}`, ...validate(input) }
    fixtureStore.products.push(product)
    return product
  }),
  update: (id, input) => fixtureRequest(() => {
    requireSession(['Admin'])
    const product = findProduct(id)
    Object.assign(product, validate(input))
    return product
  }),
  adjustStock: (id, stock) => fixtureRequest(() => {
    requireSession(managers)
    const product = findProduct(id)
    const error = validateProduct({ ...product, stock }).stock
    if (error) throw new Error(error)
    product.stock = Number(stock)
    return product
  }),
  remove: (id) => fixtureRequest(() => {
    requireSession(['Admin'])
    findProduct(id)
    fixtureStore.products = fixtureStore.products.filter((product) => product.id !== id)
    return null
  }),
}
