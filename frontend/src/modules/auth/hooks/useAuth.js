import { InteractionStatus } from '@azure/msal-browser'
import { useMsal } from '@azure/msal-react'
import { loginRequest } from '../../../config/msalConfig'

export default function useAuth() {
  const { instance, accounts, inProgress } = useMsal()
  const account = accounts[0] ?? null
  const claims = account?.idTokenClaims ?? {}
  const roles = Array.isArray(claims.roles) ? claims.roles : []

  return {
    isAuthenticated: Boolean(account),
    isInitializing: inProgress !== InteractionStatus.None && !account,
    user: account
      ? {
          id: claims.oid ?? claims.sub ?? account.homeAccountId,
          name: claims.name ?? account.name ?? '',
          username: claims.preferred_username ?? claims.email ?? claims.emails?.[0] ?? '',
        }
      : null,
    roles,
    login: () => instance.loginRedirect(loginRequest),
    logout: () => instance.logoutRedirect(),
  }
}
