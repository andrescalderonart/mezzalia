import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

/**
 * Gating de ROL, en una capa separada de ProtectedRoute (que solo verifica
 * "hay sesion iniciada", sin mirar el rol). Hace falta esta capa aparte
 * porque SUPER_ADMIN es un usuario sin tenant a proposito (TenantContext
 * vacio -- ver JwtAuthenticationFilter en el backend): las pantallas
 * tenant-scoped (menu/mesas/pedidos) ahora le devuelven
 * SinTenantEnContextoException si intenta usarlas (ver
 * fix/superadmin-fuga-listados-multitenant), y a la inversa, /tenants es
 * exclusivo de SUPER_ADMIN. Sin este componente, ProtectedRoute solo
 * evitaba que un usuario sin sesion entrara -- nada evitaba que un
 * SUPER_ADMIN aterrizara en /mesas y se encontrara con un error de
 * backend, ni que un ADMIN_RESTAURANTE navegara a /tenants a mano.
 *
 * `permitidos` es la lista blanca de roles que pueden ver esta rama del
 * arbol de rutas -- si el rol actual no esta ahi, redirige a "/" (que a su
 * vez decide el destino correcto segun el rol, ver InicioRedirect.jsx) en
 * vez de mostrar una pantalla en blanco o un error crudo.
 */
export default function RutaConRol({ permitidos }) {
  const { usuario } = useAuth()
  if (!usuario || !permitidos.includes(usuario.rol)) {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
