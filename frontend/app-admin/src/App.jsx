import { Navigate, Route, Routes } from 'react-router-dom'
import Login from './pages/Login.jsx'
import MenuPage from './pages/MenuPage.jsx'
import MesasPage from './pages/MesasPage.jsx'
import PedidosPage from './pages/PedidosPage.jsx'
import Layout from './components/Layout.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/menu" element={<MenuPage />} />
          <Route path="/mesas" element={<MesasPage />} />
          <Route path="/pedidos" element={<PedidosPage />} />
          {/* Pedidos (no /menu) es la pantalla por defecto: es el uso
              operativo del dia a dia durante un turno (para ambos roles,
              ver RF-09) -- editar la carta es una tarea ocasional de
              configuracion, no lo primero que alguien necesita ver al
              entrar. */}
          <Route path="/" element={<Navigate to="/pedidos" replace />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
