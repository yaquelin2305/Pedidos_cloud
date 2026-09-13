export function getHttpErrorMessage(error, fallback = 'No se pudo completar la solicitud. Vuelve a intentarlo.') {
  const message = error?.response?.data?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}
