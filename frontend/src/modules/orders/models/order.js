export const orderStates = {
  CREATED: 'Creado', ACCEPTED: 'Aceptado', PREPARING: 'En preparación',
  DISPATCHED: 'Despachado', DELIVERED: 'Entregado', CANCELLED: 'Cancelado',
}
export const orderTransitions = {
  CREATED: ['ACCEPTED', 'CANCELLED'], ACCEPTED: ['PREPARING', 'CANCELLED'],
  PREPARING: ['DISPATCHED', 'CANCELLED'], DISPATCHED: ['DELIVERED'], DELIVERED: [], CANCELLED: [],
}
const actionLabels = { ACCEPTED: 'Aceptar', PREPARING: 'Preparar', DISPATCHED: 'Despachar', DELIVERED: 'Entregar', CANCELLED: 'Cancelar' }

export function getOrderActions(status, roles, isOwner = false) {
  const targets = orderTransitions[status] ?? []
  const isManager = roles.some((role) => ['Admin', 'Operator'].includes(role))
  return targets.filter((target) => isManager || (roles.includes('Customer') && isOwner && status === 'CREATED' && target === 'CANCELLED'))
    .map((status) => ({ status, label: actionLabels[status] }))
}

export function validateOrder(input) {
  const errors = {}
  if (!input.customerName?.trim()) errors.customerName = 'Ingresa el nombre del cliente.'
  if (!input.items?.length) errors.items = 'Agrega al menos un producto.'
  else if (input.items.some((item) => !item.productId || !Number.isSafeInteger(Number(item.quantity)) || Number(item.quantity) <= 0)) errors.items = 'Selecciona productos con cantidades enteras mayores que cero.'
  else if (new Set(input.items.map((item) => item.productId)).size !== input.items.length) errors.items = 'Cada producto debe aparecer una sola vez.'
  return errors
}
