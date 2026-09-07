import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

/**
 * Gate para el requisito RF-10 PR4 (ver TokensRespuesta.requiereSeleccionSucursal
 * en el backend y AuthService.emitirPar/seleccionarSucursal): un MESERO sin
 * sucursalId en su access token actual SIEMPRE recibe 409
 * SucursalNoSeleccionadaException de Mesas/Pedidos/Carta (ver
 * sucursal.SucursalContext y "sucursalActual()" en MesaService/PedidoService)
 * -- en vez de dejarlo pisar esas pantallas y toparse con ese error, lo
 * redirige primero a elegir sucursal.
 *
 * Deliberadamente en un componente aparte de RutaConRol (que solo mira el
 * rol, no este dato de sesión) y de ProtectedRoute (que solo mira si hay
 * sesión) -- mismo criterio de capas separadas que ya usa el resto de
 * App.jsx (ver el javadoc de RutaConRol.jsx).
 *
 * NO aplica a ADMIN_RESTAURANTE: para ese rol, sucursalId ausente es "ver
 * todas las sucursales del tenant", un estado legítimo y el default (ver
 * AuthService.misSucursalesDisponibles) -- solo MESERO lo necesita
 * obligatoriamente. Esto es solo la conveniencia de UI (evita el flash de
 * un error 409 crudo); la garantía real sigue siendo del backend -- ver
 * también SucursalNoSeleccionadaError/useApiJson en AuthContext.jsx, que
 * atrapa el caso en que igual se cuela (por ejemplo, el token pierde el
 * claim a mitad de turno tras un refresh y la reselección automática
 * falla) y redirige desde ahí también.
 */
export default function RequiereSucursal() {
  const { usuario } = useAuth()
  const location = useLocation()

  if (usuario?.rol === 'MESERO' && usuario.sucursalId == null) {
    return <Navigate to="/seleccionar-sucursal" replace state={{ desde: location.pathname }} />
  }
  return <Outlet />
}
