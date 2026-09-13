import test from 'node:test'
import assert from 'node:assert/strict'
import { getHttpErrorMessage } from './httpError.js'

test('devuelve el mensaje del backend en lugar del mensaje de axios', () => {
  assert.equal(getHttpErrorMessage({
    message: 'Request failed with status code 409',
    response: { data: { message: 'Stock insuficiente.' } },
  }), 'Stock insuficiente.')
})

test('usa el respaldo sin respuesta HTTP o sin un mensaje utilizable', () => {
  for (const error of [undefined, null, new Error('Network Error'), { response: {} },
    ...[null, '', '  ', 123, {}].map((message) => ({ response: { data: { message } } }))]) {
    assert.equal(getHttpErrorMessage(error, 'Intenta nuevamente.'), 'Intenta nuevamente.')
  }
  assert.equal(getHttpErrorMessage({}), 'No se pudo completar la solicitud. Vuelve a intentarlo.')
})
