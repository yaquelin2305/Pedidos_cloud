import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { EventType } from '@azure/msal-browser'
import { MsalProvider } from '@azure/msal-react'
import { msalInstance } from './config/msalInstance'
import App from './App.jsx'
import './index.css'

// MSAL debe inicializarse y procesar el redirect antes de renderizar React: hacerlo dentro
// de un efecto duplicaria el intercambio del codigo si el componente se monta dos veces.
async function bootstrap() {
  await msalInstance.initialize()

  const response = await msalInstance.handleRedirectPromise()
  const activeAccount = response?.account ?? msalInstance.getAllAccounts()[0]
  if (activeAccount) {
    msalInstance.setActiveAccount(activeAccount)
  }

  msalInstance.addEventCallback((event) => {
    if (event.eventType === EventType.LOGIN_SUCCESS && event.payload?.account) {
      msalInstance.setActiveAccount(event.payload.account)
    }
  })

  createRoot(document.getElementById('root')).render(
    <MsalProvider instance={msalInstance}>
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <App />
      </BrowserRouter>
    </MsalProvider>,
  )
}

bootstrap()
