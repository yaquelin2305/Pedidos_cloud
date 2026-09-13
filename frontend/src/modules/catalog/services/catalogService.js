import apiClient from '../../../shared/http/apiClient'
import { getHttpErrorMessage } from '../../../shared/utils/httpError'
import { validateProduct } from '../models/product'

// El backend devuelve sku, category, active y timestamps; la vista solo usa este subconjunto.
function fromBackendProduct(product) {
  return {
    id: product.id,
    name: product.name,
    description: product.description ?? '',
    price: product.price,
    stock: product.stock,
  }
}

function toBackendProduct(input) {
  return {
    name: input.name.trim(),
    description: input.description?.trim() ?? '',
    price: Number(input.price),
    stock: Number(input.stock),
  }
}

async function unwrap(promise) {
  try {
    const { data } = await promise
    return data
  } catch (error) {
    throw new Error(getHttpErrorMessage(error))
  }
}

function validate(input) {
  const errors = validateProduct(input)
  if (Object.keys(errors).length) throw new Error(Object.values(errors)[0])
}

export const catalogService = {
  list: async () => (await unwrap(apiClient.get('/api/catalog/products'))).map(fromBackendProduct),

  getById: async (id) => fromBackendProduct(await unwrap(apiClient.get(`/api/catalog/products/${id}`))),

  create: async (input) => {
    validate(input)
    return fromBackendProduct(await unwrap(apiClient.post('/api/catalog/products', toBackendProduct(input))))
  },

  update: async (id, input) => {
    validate(input)
    return fromBackendProduct(await unwrap(apiClient.put(`/api/catalog/products/${id}`, toBackendProduct(input))))
  },

  adjustStock: async (id, stock) => {
    if (stock === '' || !Number.isSafeInteger(Number(stock)) || Number(stock) < 0) {
      throw new Error('Ingresa un stock entero igual o mayor que cero.')
    }
    return fromBackendProduct(
      await unwrap(apiClient.patch(`/api/catalog/products/${id}/stock`, { stock: Number(stock) })),
    )
  },

  remove: async (id) => {
    await unwrap(apiClient.delete(`/api/catalog/products/${id}`))
  },
}
