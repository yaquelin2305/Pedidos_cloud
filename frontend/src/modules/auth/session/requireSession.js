import { msalInstance } from '../../../config/msalInstance'

/**
 * Deriva la sesion vigente desde la cuenta activa de MSAL. La usan los services de fixtures
 * de orders y catalog para simular la autorizacion mientras no consumen el backend real:
 * cuando se conecten a los endpoints de verdad, esta autorizacion la hace el backend y esta
 * funcion deja de usarse.
 */
export function requireSession(allowedRoles) {
  const account = msalInstance.getActiveAccount()
  if (!account) {
    throw new Error('Tu sesión ha finalizado. Vuelve a iniciar sesión.')
  }
  const claims = account.idTokenClaims ?? {}
  const roles = Array.isArray(claims.roles) ? claims.roles : []
  if (allowedRoles && !allowedRoles.some((role) => roles.includes(role))) {
    throw new Error('No tienes permisos para realizar esta acción.')
  }
  return {
    user: {
      id: claims.oid ?? claims.sub ?? account.homeAccountId,
      name: claims.name ?? account.name ?? '',
    },
    role: roles[0] ?? null,
    roles,
  }
}
