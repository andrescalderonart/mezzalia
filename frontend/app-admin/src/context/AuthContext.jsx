import { createContext, useCallback, useContext, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  apiFetch,
  apiFetchJson,
  decodificarJwt,
  guardarAccessToken,
  guardarSucursalSeleccionada,
  guardarTokens,
  intentarSeleccionAutomatica,
  limpiarSucursalSeleccionada,
  limpiarTokens,
  obtenerAccessToken,
  obtenerRefreshToken,
  SesionExpiradaError,
} from '../api/client.js'

const AuthContext = createContext(null)

function usuarioDesdeToken(token) {
  if (!token) return null
  const claims = decodificarJwt(token)
  if (!claims) return null
  // sucursalId (RF-10 PR4): null para ADMIN_RESTAURANTE/SUPER_ADMIN
  // siempre (ven todas las sucursales sin elegir ninguna, ver
  // AuthService.emitirPar), y para MESERO solo despues de pasar por
  // POST /api/admin/sesion/sucursal -- ver SeleccionarSucursal.jsx y
  // RequiereSucursal.jsx, que usan este campo para decidir si mostrar la
  // pantalla de eleccion.
  return {
    username: claims.sub,
    rol: claims.rol,
    tenantId: claims.tenantId ?? null,
    sucursalId: claims.sucursalId ?? null,
  }
}

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(() => usuarioDesdeToken(obtenerAccessToken()))
  const navigate = useNavigate()

  /**
   * RF-10 PR4: devuelve `{ requiereSeleccionManual }` ademas de dejar
   * `usuario` actualizado -- Login.jsx lo usa para decidir si mandar a
   * /seleccionar-sucursal o dejar que "/" (InicioRedirect) resuelva el
   * destino normal segun el rol. `requiereSeleccionManual` es false tanto
   * para ADMIN_RESTAURANTE/SUPER_ADMIN (nunca aplica) como para un MESERO
   * al que intentarSeleccionAutomatica ya le resolvio la sucursal sola con
   * la que tenia guardada de una sesion anterior en este navegador.
   */
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
    const accessTokenFinal = await intentarSeleccionAutomatica(datos.accessToken, datos.requiereSeleccionSucursal)
    const usuarioFinal = usuarioDesdeToken(accessTokenFinal)
    setUsuario(usuarioFinal)
    return { requiereSeleccionManual: Boolean(datos.requiereSeleccionSucursal) && !usuarioFinal?.sucursalId }
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

  /**
   * RF-10 PR4: usado por SeleccionarSucursal.jsx (eleccion manual explicita
   * del MESERO) -- re-emite el access token con el claim sucursalId puesto
   * (ver AccessTokenRespuesta, a proposito sin refreshToken) y sincroniza
   * `usuario` en React para que RequiereSucursal.jsx dejé de redirigir.
   */
  const seleccionarSucursal = useCallback(
    async (sucursalId) => {
      try {
        const datos = await apiFetchJson('/api/admin/sesion/sucursal', {
          method: 'POST',
          body: { sucursalId },
        })
        guardarAccessToken(datos.accessToken)
        guardarSucursalSeleccionada(sucursalId)
        setUsuario(usuarioDesdeToken(datos.accessToken))
      } catch (e) {
        if (e instanceof SesionExpiradaError) {
          sesionExpirada()
        }
        throw e
      }
    },
    [sesionExpirada],
  )

  /**
   * RF-10 PR4: reaccion centralizada al 409 "SUCURSAL_NO_SELECCIONADA" (ver
   * useApiJson mas abajo) -- cubre tanto el caso esperado (un MESERO recien
   * logueado que todavia no eligio) como el caso limite real de que
   * intentarSeleccionAutomatica haya fallado en un refresh silencioso en
   * segundo plano (por ejemplo, el ADMIN_RESTAURANTE le quito esa sucursal
   * mientras el MESERO seguia con la pestaña abierta en /pedidos): en
   * cualquiera de los dos casos, la sucursal guardada en este navegador ya
   * no sirve y hay que volver a preguntarle, sin necesidad de deslogearlo
   * (el access token sigue siendo valido para todo lo que NO sea
   * sucursal-scoped).
   */
  const sucursalNoSeleccionada = useCallback(() => {
    limpiarSucursalSeleccionada()
    setUsuario((prev) => (prev ? { ...prev, sucursalId: null } : prev))
    navigate('/seleccionar-sucursal', { replace: true })
  }, [navigate])

  return (
    <AuthContext.Provider
      value={{ usuario, login, logout, sesionExpirada, seleccionarSucursal, sucursalNoSeleccionada }}
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
 * apiFetchJson en client.js. Tambien redirige a /login si el refresh falla,
 * y a /seleccionar-sucursal (RF-10 PR4) si el backend responde el 409
 * "SUCURSAL_NO_SELECCIONADA" -- ver sucursalNoSeleccionada en este mismo
 * archivo. Esto cubre CUALQUIER llamada hecha con este hook, no solo
 * Mesas/Pedidos: si mañana otro endpoint sucursal-scoped empieza a exigir
 * el claim (ver el aviso de alcance en SucursalContext.requiereSeleccion),
 * queda cubierto automaticamente sin tocar cada pantalla una por una.
 */
export function useApiJson() {
  const { sesionExpirada, sucursalNoSeleccionada } = useAuth()
  return useCallback(
    async (path, opciones) => {
      try {
        return await apiFetchJson(path, opciones)
      } catch (e) {
        if (e instanceof SesionExpiradaError) {
          sesionExpirada()
        } else if (e.codigo === 'SUCURSAL_NO_SELECCIONADA') {
          sucursalNoSeleccionada()
        }
        throw e
      }
    },
    [sesionExpirada, sucursalNoSeleccionada],
  )
}
