const moneyFormatter = new Intl.NumberFormat('es-CL', {
  style: 'currency', currency: 'CLP', minimumFractionDigits: 0, maximumFractionDigits: 2,
})
const dateFormatter = new Intl.DateTimeFormat('es-CL', { dateStyle: 'medium' })

export const formatMoney = (value) => moneyFormatter.format(value)
export const formatDate = (value) => dateFormatter.format(new Date(value))
