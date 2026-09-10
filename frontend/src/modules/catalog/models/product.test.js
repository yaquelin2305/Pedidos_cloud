import test from 'node:test'
import assert from 'node:assert/strict'
import { validateProduct } from './product.js'

const product = { name: 'Cuaderno', price: 2490, stock: 5 }

test('permite precio cero y stock cero pero exige nombre', () => {
  assert.deepEqual(validateProduct({ ...product, price: 0, stock: 0 }), {})
  assert.ok(validateProduct({ ...product, name: '  ' }).name)
})

test('rechaza precios negativos, vacios o no finitos', () => {
  for (const price of ['', -1, Infinity, NaN, 'abc']) assert.ok(validateProduct({ ...product, price }).price)
})

test('el stock debe ser entero, seguro y no negativo', () => {
  for (const stock of ['', -1, 0.5, Infinity, NaN, Number.MAX_SAFE_INTEGER + 1]) assert.ok(validateProduct({ ...product, stock }).stock)
})
