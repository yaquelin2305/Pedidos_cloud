const key = 'pedidos360.session'
const listeners = new Set()
export const availableRoles = ['Admin', 'Operator', 'Customer']
const identity = { id: 'customer-1', name: 'Camila Torres', username: 'camila.torres@example.com' }

function restore() {
  try {
    const stored = JSON.parse(sessionStorage.getItem(key))
    if (stored?.user?.id === identity.id && availableRoles.includes(stored.role)) return stored
  } catch { /* Una sesión ilegible se trata como una sesión cerrada. */ }
  return null
}

let session = restore()
function publish(next) {
  session = next
  try {
    if (next) sessionStorage.setItem(key, JSON.stringify(next))
    else sessionStorage.removeItem(key)
  } catch { /* La sesión sigue disponible en memoria si se bloquea el almacenamiento. */ }
  listeners.forEach((listener) => listener())
}

export const sessionStore = {
  subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener) },
  getSnapshot: () => session,
  login: async () => publish({ user: identity, role: 'Admin' }),
  register: async () => publish({ user: identity, role: 'Customer' }),
  logout: () => publish(null),
  setRole(role) {
    if (session && availableRoles.includes(role)) publish({ ...session, role })
  },
}

export function requireSession(roles = availableRoles) {
  if (!session) throw new Error('Tu sesión ha finalizado. Vuelve a iniciar sesión.')
  if (!roles.includes(session.role)) throw new Error('No tienes permisos para realizar esta acción.')
  return session
}
