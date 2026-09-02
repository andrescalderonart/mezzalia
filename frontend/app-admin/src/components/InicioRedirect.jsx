import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

/**
 * "/" no puede ser un <Navigate> estatico a una sola ruta: el destino
 * depende del rol. SUPER_ADMIN no tiene tenant -- el backend le rechaza
 * menu/mesas/pedidos (ver fix/superadmin-fuga-listados-multitenant), asi
 * que su pantalla de aterrizaje es /tenants. ADMIN_RESTAURANTE/MESERO
 * siguen yendo a /pedidos (uso operativo del dia a dia durante un turno,
 * ver el comentario original que ya tenia App.jsx sobre por que pedidos y
 * no menu).
 */
export default function InicioRedirect() {
  const { usuario } = useAuth()
  const destino = usuario?.rol === 'SUPER_ADMIN' ? '/tenants' : '/pedidos'
  return <Navigate to={destino} replace />
}
