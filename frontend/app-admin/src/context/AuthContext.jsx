import { createContext, useCallback, useContext, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  apiFetch,
  apiFetchJson,
  decodificarJwt,
  guardarTokens,
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
  return { username: claims.sub, rol: claims.rol, tenantId: claims.tenantId ?? null }
}

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(() => usuarioDesdeToken(obtenerAccessToken()))
  const navigate = useNavigate()

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
    setUsuario(usuarioDesdeToken(datos.accessToken))
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

  return (
    <AuthContext.Provider value={{ usuario, login, logout, sesionExpirada }}>
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
 * apiFetchJson en client.js. Tambien redirige a /login si el refresh falla.
 */
export function useApiJson() {
  const { sesionExpirada } = useAuth()
  return useCallback(
    async (path, opciones) => {
      try {
        return await apiFetchJson(path, opciones)
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
