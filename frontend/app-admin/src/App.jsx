import { Navigate, Route, Routes } from 'react-router-dom'
import Login from './pages/Login.jsx'
import MenuPage from './pages/MenuPage.jsx'
import MesasPage from './pages/MesasPage.jsx'
import PedidosPage from './pages/PedidosPage.jsx'
import TenantsPage from './pages/TenantsPage.jsx'
import SucursalesPage from './pages/SucursalesPage.jsx'
import UsuariosPage from './pages/UsuariosPage.jsx'
import SeleccionarSucursal from './pages/SeleccionarSucursal.jsx'
import Layout from './components/Layout.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import RutaConRol from './components/RutaConRol.jsx'
import RequiereSucursal from './components/RequiereSucursal.jsx'
import InicioRedirect from './components/InicioRedirect.jsx'
const ROLES_RESTAURANTE = ['ADMIN_RESTAURANTE', 'MESERO']

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route element={<ProtectedRoute />}>
        {/* RF-10 PR4: fuera de <Layout> a propósito -- ver el comentario
            en SeleccionarSucursal.jsx sobre por qué no tiene sentido
            mostrar el sidebar de Pedidos/Mesas/Carta antes de que el
            MESERO pueda usarlos de verdad. */}
        <Route path="/seleccionar-sucursal" element={<SeleccionarSucursal />} />

        <Route element={<Layout />}>
          <Route element={<RutaConRol permitidos={ROLES_RESTAURANTE} />}>
            {/* /menu (la carta) es la única de las tres compartida entre
                todas las sucursales de un tenant -- decisión de negocio
                explícita (ver V17__usuario_sucursales.sql), por eso no
                está bajo RequiereSucursal como las otras dos. */}
            <Route path="/menu" element={<MenuPage />} />
            <Route element={<RequiereSucursal />}>
              <Route path="/mesas" element={<MesasPage />} />
              <Route path="/pedidos" element={<PedidosPage />} />
            </Route>
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

          <Route element={<RutaConRol permitidos={['ADMIN_RESTAURANTE']} />}>
            <Route path="/sucursales" element={<SucursalesPage />} />
          </Route>

          {/* El destino de "/" depende del rol -- ver InicioRedirect.jsx.
              Para ADMIN_RESTAURANTE/MESERO sigue siendo /pedidos (uso
              operativo del dia a dia durante un turno, ver RF-09); un
              SUPER_ADMIN no tiene tenant y aterriza en /tenants. Un
              MESERO sin sucursal elegida todavía "aterriza" en /pedidos
              igual, pero RequiereSucursal lo intercepta ahí mismo y lo
              manda a /seleccionar-sucursal -- InicioRedirect no necesita
              saber nada de RF-10. */}
          <Route path="/" element={<InicioRedirect />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
