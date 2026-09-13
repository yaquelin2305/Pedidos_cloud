import { useEffect, useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { apiRequest } from '../../../config/msalConfig'
import { decodeJwtPayload } from '../../../shared/utils/jwt'

// Pide el access token del API en segundo plano y lee su claim "scp". Sirve para evidenciar
// que la app obtiene el token necesario para consumir el API Gateway y lee sus scopes.
export default function useApiScopes() {
  const { instance, accounts } = useMsal()
  const [scopes, setScopes] = useState([])

  useEffect(() => {
    const account = accounts[0]
    if (!account) {
      setScopes([])
      return undefined
    }
    let active = true
    instance
      .acquireTokenSilent({ ...apiRequest, account })
      .then((result) => {
        if (!active) return
        const payload = decodeJwtPayload(result.accessToken)
        const scp = payload?.scp ?? ''
        setScopes(scp ? scp.split(' ').filter(Boolean) : [])
      })
      .catch(() => {
        if (active) setScopes([])
      })
    return () => {
      active = false
    }
  }, [instance, accounts])

  return scopes
}
