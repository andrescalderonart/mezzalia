import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

export default function ProtectedRoute() {
  const { usuario } = useAuth()
  if (!usuario) return <Navigate to="/login" replace />
  return <Outlet />
}
