export function validateProduct(product) {
  const errors = {}
  if (!product.name?.trim()) errors.name = 'Ingresa el nombre del producto.'
  if (product.price === '' || !Number.isFinite(Number(product.price)) || Number(product.price) < 0) errors.price = 'Ingresa un precio igual o mayor que cero.'
  if (product.stock === '' || !Number.isSafeInteger(Number(product.stock)) || Number(product.stock) < 0) errors.stock = 'Ingresa un stock entero igual o mayor que cero.'
  return errors
}
