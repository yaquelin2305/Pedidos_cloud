import { env } from './env'

export const msalConfig = {
  auth: {
    clientId: env.azureClientId,
    authority: env.azureAuthority,
    knownAuthorities: env.azureKnownAuthority ? [env.azureKnownAuthority] : [],
    redirectUri: env.azureRedirectUri,
    postLogoutRedirectUri: env.azurePostLogoutRedirectUri,
  },
  cache: {
    cacheLocation: 'sessionStorage',
    storeAuthStateInCookie: false,
  },
}

// Scopes de identidad: los pide el login. No dan acceso al API.
export const loginRequest = {
  scopes: ['openid', 'profile', 'offline_access'],
}

// Scope del API: se pide aparte, es el unico access token que viaja al backend.
export const apiRequest = {
  scopes: [env.apiScope],
}
