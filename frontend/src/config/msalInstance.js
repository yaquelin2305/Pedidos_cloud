import { PublicClientApplication } from '@azure/msal-browser'
import { msalConfig } from './msalConfig'

// Instancia unica, compartida por main.jsx, apiClient y los hooks de auth.
export const msalInstance = new PublicClientApplication(msalConfig)
