const required = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL,
  apiScope: import.meta.env.VITE_API_SCOPE,
  azureClientId: import.meta.env.VITE_AZURE_CLIENT_ID,
  azureAuthority: import.meta.env.VITE_AZURE_AUTHORITY,
  azureRedirectUri: import.meta.env.VITE_AZURE_REDIRECT_URI,
  azurePostLogoutRedirectUri: import.meta.env.VITE_AZURE_POST_LOGOUT_REDIRECT_URI,
}

const missing = Object.entries(required)
  .filter(([, value]) => !value)
  .map(([key]) => key)

if (missing.length > 0) {
  throw new Error(`Faltan variables de entorno obligatorias: ${missing.join(', ')}`)
}

export const env = {
  ...required,
  // Solo se necesita en tenants External ID/CIAM; en workforce queda vacio.
  azureKnownAuthority: import.meta.env.VITE_AZURE_KNOWN_AUTHORITY || '',
}
