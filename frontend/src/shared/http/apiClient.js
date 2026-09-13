import axios from 'axios'
import { InteractionRequiredAuthError } from '@azure/msal-browser'
import { env } from '../../config/env'
import { apiRequest } from '../../config/msalConfig'
import { msalInstance } from '../../config/msalInstance'

const apiClient = axios.create({ baseURL: env.apiBaseUrl })

async function acquireAccessToken() {
  const account = msalInstance.getActiveAccount()
  if (!account) {
    return null
  }
  try {
    const result = await msalInstance.acquireTokenSilent({ ...apiRequest, account })
    return result.accessToken
  } catch (error) {
    if (error instanceof InteractionRequiredAuthError) {
      // No se puede resolver sin interaccion: se dispara el redirect y la peticion actual
      // se descarta, la pagina esta por navegar de todas formas.
      await msalInstance.acquireTokenRedirect({ ...apiRequest, account })
      return null
    }
    throw error
  }
}

apiClient.interceptors.request.use(async (config) => {
  const accessToken = await acquireAccessToken()
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Token invalido o sesion caida: se relanza el login. La promesa nunca resuelve
      // porque la pagina esta a punto de navegar.
      await msalInstance.loginRedirect(apiRequest)
      return new Promise(() => {})
    }
    // 403 (rol insuficiente) se propaga tal cual: la pagina muestra el error y la
    // sesion sigue activa, no se cierra.
    return Promise.reject(error)
  },
)

export default apiClient
