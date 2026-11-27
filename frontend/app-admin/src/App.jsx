import { Navigate, Route, Routes } from 'react-router-dom'
import Login from './pages/Login.jsx'
import MenuPage from './pages/MenuPage.jsx'
import MesasPage from './pages/MesasPage.jsx'
import PedidosPage from './pages/PedidosPage.jsx'
import TenantsPage from './pages/TenantsPage.jsx'
import SucursalesPage from './pages/SucursalesPage.jsx'
import UsuariosPage from './pages/UsuariosPage.jsx'
import ReportesPage from './pages/ReportesPage.jsx'
import Layout from './components/Layout.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import RutaConRol from './components/RutaConRol.jsx'
import RequiereSucursal from './components/RequiereSucursal.jsx'
import InicioRedirect from './components/InicioRedirect.jsx'
import SeleccionarSucursalPage from './pages/SeleccionarSucursalPage.jsx'
const ROLES_RESTAURANTE = ['ADMIN_RESTAURANTE', 'MESERO']

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route element={<ProtectedRoute />}>
        {/* RF-10 PR4: fuera de <Layout> a proposito (como /login) -- ver el
            javadoc de SeleccionarSucursalPage.jsx. Necesita sesion (por eso
            va DENTRO de ProtectedRoute) pero todavia no un sucursalId, asi
            que no puede ir dentro de RequiereSucursal (seria un loop). */}
        <Route path="/seleccionar-sucursal" element={<SeleccionarSucursalPage />} />

        <Route element={<Layout />}>
          <Route element={<RutaConRol permitidos={ROLES_RESTAURANTE} />}>
            {/* RF-10 PR4: un MESERO sin sucursalId en su access token
                actual SIEMPRE recibe 409 de estas tres pantallas (ver
                sucursal.SucursalContext/"sucursalActual()" en el backend) --
                RequiereSucursal.jsx lo manda antes a elegir una. No aplica a
                ADMIN_RESTAURANTE (ver el javadoc de ese componente). */}
            <Route element={<RequiereSucursal />}>
              <Route path="/menu" element={<MenuPage />} />
              <Route path="/mesas" element={<MesasPage />} />
              <Route path="/pedidos" element={<PedidosPage />} />
            </Route>
          </Route>

          {/* Gestión de usuarios internos (RF-09/§2): exclusivo de
              ADMIN_RESTAURANTE, ni siquiera MESERO puede dar de alta o
              desactivar otras cuentas del restaurante. */}
          <Route element={<RutaConRol permitidos={['ADMIN_RESTAURANTE']} />}>
            <Route path="/usuarios" element={<UsuariosPage />} />
            <Route path="/reportes" element={<ReportesPage />} />
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
              SUPER_ADMIN no tiene tenant y aterriza en /tenants. */}
          <Route path="/" element={<InicioRedirect />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
