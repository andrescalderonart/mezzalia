import { Navigate, Route, Routes } from 'react-router-dom'
import Login from './pages/Login.jsx'
import MenuPage from './pages/MenuPage.jsx'
import MesasPage from './pages/MesasPage.jsx'
import PedidosPage from './pages/PedidosPage.jsx'
import TenantsPage from './pages/TenantsPage.jsx'
import UsuariosPage from './pages/UsuariosPage.jsx'
import Layout from './components/Layout.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import RutaConRol from './components/RutaConRol.jsx'
import InicioRedirect from './components/InicioRedirect.jsx'

// SUPER_ADMIN no pertenece a ningun tenant (a proposito, ver
// UsuarioInterno/JwtAuthenticationFilter en el backend) -- menu/mesas/
// pedidos son exclusivos de los roles que si operan un restaurante.
const ROLES_RESTAURANTE = ['ADMIN_RESTAURANTE', 'MESERO']

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route element={<RutaConRol permitidos={ROLES_RESTAURANTE} />}>
            <Route path="/menu" element={<MenuPage />} />
            <Route path="/mesas" element={<MesasPage />} />
            <Route path="/pedidos" element={<PedidosPage />} />
          </Route>

          {/* Gestión de usuarios internos (RF-09/§2): exclusivo de
              ADMIN_RESTAURANTE, ni siquiera MESERO puede dar de alta o
              desactivar otras cuentas del restaurante. */}
          <Route element={<RutaConRol permitidos={['ADMIN_RESTAURANTE']} />}>
            <Route path="/usuarios" element={<UsuariosPage />} />
          </Route>

          <Route element={<RutaConRol permitidos={['SUPER_ADMIN']} />}>
            <Route path="/tenants" element={<TenantsPage />} />
          </Route>

          {/* El destino de "/" depende del rol -- ver InicioRedirect.jsx.
              Para ADMIN_RESTAURANTE/MESERO sigue siendo /pedidos (uso
              operativo del dia a dia durante un turno, ver RF-09); un
              SUPER_ADMIN no tiene tenant y aterriza en /tenants. */}
          <Route path="/" element={<InicioRedirect />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
