import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  actualizarAccessToken,
  alCambiarAccessToken,
  apiFetch,
  apiFetchJson,
  decodificarJwt,
  guardarTokens,
  guardarUltimaSucursalId,
  intentarReseleccionAutomatica,
  limpiarTokens,
  obtenerAccessToken,
  obtenerRefreshToken,
  SesionExpiradaError,
  SucursalNoSeleccionadaError,
} from '../api/client.js'

const AuthContext = createContext(null)

function usuarioDesdeToken(token) {
  if (!token) return null
  const claims = decodificarJwt(token)
  if (!claims) return null
  return {
    username: claims.sub,
    rol: claims.rol,
    tenantId: claims.tenantId ?? null,
    // RF-10 PR4: ausente (no null) cuando este MESERO todavía no eligió (o
    // perdió, tras un refresh -- ver JwtService/AuthService en el backend)
    // la sucursal en la que va a trabajar EN ESTE access token. Para
    // ADMIN_RESTAURANTE, ausente es un estado legítimo ("ver todas las
    // sucursales del tenant"), no un problema -- ver RequiereSucursal.jsx,
    // que es el único lugar que le da un significado obligatorio a este
    // campo, y solo para MESERO.
    sucursalId: claims.sucursalId ?? null,
  }
}

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(() => usuarioDesdeToken(obtenerAccessToken()))
  const navigate = useNavigate()

  // Mantiene `usuario` sincronizado con el access token real de
  // localStorage cada vez que cambia por fuera de login()/logout(): un
  // refresh silencioso disparado por apiFetch, o una (re)selección de
  // sucursal -- ver alCambiarAccessToken en client.js. Sin esto, tras un
  // refresh el claim sucursalId en el estado de React se quedaría con el
  // valor viejo aunque el token nuevo ya lo haya perdido (o ganado).
  useEffect(() => {
    alCambiarAccessToken(() => setUsuario(usuarioDesdeToken(obtenerAccessToken())))
    return () => alCambiarAccessToken(() => {})
  }, [])

  const login = useCallback(async (username, password) => {
    const resp = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    const datos = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error(datos.mensaje || 'No se pudo iniciar sesión')
    }
    guardarTokens(datos)
    // RF-10 PR4: si este MESERO ya había elegido sucursal antes en este
    // mismo navegador, se la reasignamos de una (el backend revalida que
    // le siga siendo válida) -- así no tiene que volver a elegirla a mano
    // en cada login. Si falla o es la primera vez, el gate de
    // RequiereSucursal.jsx lo manda a /seleccionar-sucursal.
    await intentarReseleccionAutomatica(datos)
    setUsuario(usuarioDesdeToken(obtenerAccessToken()))
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = obtenerRefreshToken()
    limpiarTokens()
    setUsuario(null)
    navigate('/login', { replace: true })
    if (refreshToken) {
      // best-effort: si esto falla (red, servidor caído) el usuario ya salió
      // localmente igual, no vale la pena bloquear el logout por eso.
      fetch('/api/auth/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      }).catch(() => {})
    }
  }, [navigate])

  const sesionExpirada = useCallback(() => {
    limpiarTokens()
    setUsuario(null)
    navigate('/login', { replace: true })
  }, [navigate])

  // RF-10 PR4: destino cuando MesaService/PedidoService devuelven 409
  // SUCURSAL_NO_SELECCIONADA (ver SucursalNoSeleccionadaError en
  // client.js) o cuando RequiereSucursal.jsx detecta de entrada que a un
  // MESERO le falta el claim. `desde` permite volver a la pantalla
  // original después de elegir (ver SeleccionarSucursalPage.jsx).
  const irASeleccionarSucursal = useCallback(
    (desde) => {
      navigate('/seleccionar-sucursal', { replace: true, state: desde ? { desde } : undefined })
    },
    [navigate],
  )

  // Llama a POST /api/admin/sesion/sucursal, actualiza el access token
  // (solo ese -- ver AccessTokenRespuesta) y recuerda la elección para
  // poder reasignarla sola en el próximo login/refresh (ver
  // intentarReseleccionAutomatica en client.js).
  const seleccionarSucursal = useCallback(async (sucursalId) => {
    const datos = await apiFetchJson('/api/admin/sesion/sucursal', {
      method: 'POST',
      body: { sucursalId },
    })
    actualizarAccessToken(datos.accessToken)
    guardarUltimaSucursalId(sucursalId)
    setUsuario(usuarioDesdeToken(obtenerAccessToken()))
  }, [])

  return (
    <AuthContext.Provider
      value={{ usuario, login, logout, sesionExpirada, irASeleccionarSucursal, seleccionarSucursal }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>')
  return ctx
}

/**
 * apiFetch "atado" al contexto de React: si el refresh silencioso falla
 * (SesionExpiradaError), manda automáticamente a /login en vez de que cada
 * pantalla tenga que acordarse de hacer ese try/catch por su cuenta.
 */
export function useApi() {
  const { sesionExpirada } = useAuth()
  return useCallback(
    async (path, opciones) => {
      try {
        return await apiFetch(path, opciones)
      } catch (e) {
        if (e instanceof SesionExpiradaError) {
          sesionExpirada()
        }
        throw e
      }
    },
    [sesionExpirada],
  )
}

/**
 * Igual que useApi pero para el caso comun de "mandar/recibir JSON": ver
 * apiFetchJson en client.js. También redirige a /login si el refresh
 * falla, y a /seleccionar-sucursal si el backend responde 409
 * SUCURSAL_NO_SELECCIONADA (RF-10 PR4) -- ver SucursalNoSeleccionadaError.
 */
export function useApiJson() {
  const { sesionExpirada, irASeleccionarSucursal } = useAuth()
  return useCallback(
    async (path, opciones) => {
      try {
        return await apiFetchJson(path, opciones)
      } catch (e) {
        if (e instanceof SesionExpiradaError) {
          sesionExpirada()
        }
        if (e instanceof SucursalNoSeleccionadaError) {
          irASeleccionarSucursal()
        }
        throw e
      }
    },
    [sesionExpirada, irASeleccionarSucursal],
  )
}
