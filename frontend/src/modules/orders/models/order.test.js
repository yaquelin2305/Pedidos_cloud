import test from 'node:test'
import assert from 'node:assert/strict'
import { getOrderActions, orderTransitions, validateOrder } from './order.js'

test('no se puede saltar de creado o aceptado a despachado', () => {
  assert.equal(orderTransitions.CREATED.includes('DISPATCHED'), false)
  assert.equal(orderTransitions.ACCEPTED.includes('DISPATCHED'), false)
  assert.deepEqual(orderTransitions.PREPARING, ['DISPATCHED', 'CANCELLED'])
})

test('los estados terminales nunca ofrecen acciones', () => {
  for (const status of ['DELIVERED', 'CANCELLED']) {
    for (const role of ['Admin', 'Operator', 'Customer']) assert.deepEqual(getOrderActions(status, [role], true), [])
  }
})

test('Customer solo puede cancelar pedidos propios creados', () => {
  assert.deepEqual(getOrderActions('CREATED', ['Customer'], true), [{ status: 'CANCELLED', label: 'Cancelar' }])
  assert.deepEqual(getOrderActions('CREATED', ['Customer'], false), [])
  for (const status of ['ACCEPTED', 'PREPARING', 'DISPATCHED']) assert.deepEqual(getOrderActions(status, ['Customer'], true), [])
})

test('Admin y Operator tienen las mismas transiciones operativas', () => {
  for (const [status, targets] of Object.entries(orderTransitions)) {
    for (const role of ['Admin', 'Operator']) assert.deepEqual(getOrderActions(status, [role]).map((action) => action.status), targets)
  }
  assert.deepEqual(getOrderActions('CREATED', []), [])
})

test('el pedido exige cliente, productos distintos y cantidades enteras positivas', () => {
  const valid = { customerName: 'Camila Torres', items: [{ productId: 'P-001', quantity: 2 }] }
  assert.deepEqual(validateOrder(valid), {})
  assert.ok(validateOrder({ ...valid, customerName: '  ' }).customerName)
  assert.ok(validateOrder({ ...valid, items: [] }).items)
  assert.ok(validateOrder({ ...valid, items: [...valid.items, ...valid.items] }).items)
  for (const quantity of [0, -1, 1.5, Infinity, NaN, Number.MAX_SAFE_INTEGER + 1]) {
    assert.ok(validateOrder({ ...valid, items: [{ productId: 'P-001', quantity }] }).items)
  }
})
