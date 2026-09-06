import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

/**
 * RF-10 PR4: MesasPage y PedidosPage son sucursal-scoped en el backend (ver
 * MesaService/PedidoService y sucursal.SucursalContext.requiereSeleccion) --
 * un MESERO sin sucursalId en su access token actual recibe un 409
 * "SUCURSAL_NO_SELECCIONADA" de esos endpoints (ver
 * SucursalExceptions.SucursalNoSeleccionadaException). Este guard evita
 * siquiera mostrarle esas pantallas hasta que elija, en vez de dejarlo ver
 * el banner de error genérico de esas páginas.
 *
 * A propósito NO cubre /menu: la carta sigue siendo compartida entre todas
 * las sucursales de un tenant (decisión de negocio explícita, ver
 * V17__usuario_sucursales.sql) -- MenuService no exige sucursalId, así que
 * un MESERO puede ver/usar la carta aunque todavía no haya elegido dónde
 * trabajar hoy.
 *
 * Nunca aplica a ADMIN_RESTAURANTE (sucursalId siempre null para ese rol,
 * ve todas las sucursales de su tenant sin elegir ninguna -- ver
 * AuthService.emitirPar) ni a SUPER_ADMIN (RutaConRol ya lo filtra antes en
 * el árbol de rutas, ni siquiera llega hasta acá).
 */
export default function RequiereSucursal() {
  const { usuario } = useAuth()
  if (usuario?.rol === 'MESERO' && !usuario.sucursalId) {
    return <Navigate to="/seleccionar-sucursal" replace />
  }
  return <Outlet />
}
