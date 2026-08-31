import { Navigate, Route, Routes } from 'react-router-dom'
import Login from './pages/Login.jsx'
import MenuPage from './pages/MenuPage.jsx'
import Layout from './components/Layout.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/menu" element={<MenuPage />} />
          <Route path="/" element={<Navigate to="/menu" replace />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
