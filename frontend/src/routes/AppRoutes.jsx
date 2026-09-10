import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from '../modules/auth/guards/RequireAuth'
import RequireRole from '../modules/auth/guards/RequireRole'
import LoginPage from '../modules/auth/pages/LoginPage'
import AuthCallbackPage from '../modules/auth/pages/AuthCallbackPage'
import DashboardPage from '../modules/dashboard/pages/DashboardPage'
import OrdersPage from '../modules/orders/pages/OrdersPage'
import CatalogPage from '../modules/catalog/pages/CatalogPage'
import Layout from '../shared/components/Layout'
import ForbiddenPage from '../shared/components/ForbiddenPage'

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
      <Route path="/forbidden" element={<ForbiddenPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route element={<RequireRole allowedRoles={['Admin', 'Operator', 'Customer']} />}>
            <Route path="/orders" element={<OrdersPage />} />
          </Route>
          <Route element={<RequireRole allowedRoles={['Admin', 'Operator']} />}>
            <Route path="/catalog" element={<CatalogPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
