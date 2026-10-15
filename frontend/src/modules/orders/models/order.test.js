import test from 'node:test'
import assert from 'node:assert/strict'
import { getOrderActions, isOrderCorrection, orderTransitions, validateOrder } from './order.js'

test('no se puede saltar de creado o aceptado a despachado', () => {
  assert.equal(orderTransitions.CREATED.includes('DISPATCHED'), false)
  assert.equal(orderTransitions.ACCEPTED.includes('DISPATCHED'), false)
  assert.deepEqual(orderTransitions.PREPARING, ['DISPATCHED', 'ACCEPTED'])
})

test('un pedido cancelado no se reactiva', () => {
  for (const role of ['Admin', 'Operator', 'Customer']) assert.deepEqual(getOrderActions('CANCELLED', [role], true), [])
})

test('Admin y Operator pueden corregir cada hito logistico un paso atras', () => {
  const corrections = [
    ['PREPARING', 'ACCEPTED', 'Volver a aceptado'],
    ['DISPATCHED', 'PREPARING', 'Volver a preparación'],
    ['DELIVERED', 'DISPATCHED', 'Volver a despachado'],
  ]
  for (const [from, to, label] of corrections) {
    assert.equal(isOrderCorrection(from, to), true)
    for (const role of ['Admin', 'Operator']) {
      assert.ok(getOrderActions(from, [role]).some((action) => action.status === to && action.label === label))
    }
  }
  assert.equal(isOrderCorrection('DISPATCHED', 'DELIVERED'), false)
  assert.equal(isOrderCorrection('ACCEPTED', 'CREATED'), false)
  assert.equal(isOrderCorrection('CREATED', 'CANCELLED'), false)
  assert.equal(orderTransitions.ACCEPTED.includes('CREATED'), false)
})

test('Customer solo puede cancelar pedidos propios creados', () => {
  assert.deepEqual(getOrderActions('CREATED', ['Customer'], true), [{ status: 'CANCELLED', label: 'Cancelar' }])
  assert.deepEqual(getOrderActions('CREATED', ['Customer'], false), [])
  for (const status of ['ACCEPTED', 'PREPARING', 'DISPATCHED', 'DELIVERED']) assert.deepEqual(getOrderActions(status, ['Customer'], true), [])
})

test('Admin y Operator tienen las mismas transiciones operativas', () => {
  for (const [status, targets] of Object.entries(orderTransitions)) {
    for (const role of ['Admin', 'Operator']) assert.deepEqual(getOrderActions(status, [role]).map((action) => action.status), targets)
  }
  assert.deepEqual(getOrderActions('CREATED', []), [])
})

test('el pedido solo exige productos distintos y cantidades enteras positivas', () => {
  const valid = { items: [{ productId: 'P-001', quantity: 2 }] }
  assert.deepEqual(validateOrder(valid), {})
  assert.ok(validateOrder({ ...valid, items: [] }).items)
  assert.ok(validateOrder({ ...valid, items: [...valid.items, ...valid.items] }).items)
  for (const quantity of [0, -1, 1.5, Infinity, NaN, Number.MAX_SAFE_INTEGER + 1]) {
    assert.ok(validateOrder({ ...valid, items: [{ productId: 'P-001', quantity }] }).items)
  }
})

test('normaliza los ids al detectar productos duplicados', () => {
  assert.deepEqual(validateOrder({ items: [{ productId: 10, quantity: 1 }] }), {})
  assert.ok(validateOrder({ items: [
    { productId: 10, quantity: 1 },
    { productId: '10', quantity: 2 },
  ] }).items)
})
