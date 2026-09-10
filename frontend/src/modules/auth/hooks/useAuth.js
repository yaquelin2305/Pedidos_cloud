import { useSyncExternalStore } from 'react'
import { sessionStore } from '../session/sessionStore'

export default function useAuth() {
  const session = useSyncExternalStore(sessionStore.subscribe, sessionStore.getSnapshot)
  return {
    isAuthenticated: Boolean(session),
    user: session?.user ?? null,
    roles: session ? [session.role] : [],
    scopes: session ? ['orders.read', 'orders.write', ...(session.role === 'Customer' ? [] : ['catalog.read', 'catalog.write'])] : [],
    login: sessionStore.login,
    register: sessionStore.register,
    logout: sessionStore.logout,
  }
}
