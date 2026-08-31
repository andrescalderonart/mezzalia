const CLAVE_ACCESS = 'mezzalia.accessToken'
const CLAVE_REFRESH = 'mezzalia.refreshToken'

export function guardarTokens({ accessToken, refreshToken }) {
  localStorage.setItem(CLAVE_ACCESS, accessToken)
  localStorage.setItem(CLAVE_REFRESH, refreshToken)
}

export function limpiarTokens() {
  localStorage.removeItem(CLAVE_ACCESS)
  localStorage.removeItem(CLAVE_REFRESH)
}

export function obtenerAccessToken() {
  return localStorage.getItem(CLAVE_ACCESS)
}

export function obtenerRefreshToken() {
  return localStorage.getItem(CLAVE_REFRESH)
}

/**
 * Decodifica el payload de un JWT SIN verificar la firma -- el navegador no
 * tiene (ni debe tener) el JWT_SECRET. Esto es solo para leer claims
 * públicas (username, rol, tenantId) y pintar la UI (nombre de usuario, qué
 * botones mostrar según el rol). La verificación real de la firma la hace
 * SIEMPRE el backend en cada request (JwtAuthenticationFilter) -- nunca usar
 * este decode para decisiones de seguridad, solo de interfaz.
 */
export function decodificarJwt(token) {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decodeURIComponent(escape(json)))
  } catch {
    return null
  }
}

export class SesionExpiradaError extends Error {}

let refrescoEnCurso = null

async function refrescarTokens() {
  // Evita disparar varios refresh en paralelo si varias llamadas fallan con
  // 401 al mismo tiempo: todas esperan la MISMA promesa en vez de rotar el
  // refresh token varias veces (lo cual invalidaría las otras por el propio
  // mecanismo de reuso que ya probamos en el backend -- ver AuthService).
  if (!refrescoEnCurso) {
    refrescoEnCurso = (async () => {
      const refreshToken = obtenerRefreshToken()
      if (!refreshToken) throw new SesionExpiradaError('No hay refresh token')

      const resp = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!resp.ok) {
        limpiarTokens()
        throw new SesionExpiradaError('El refresh token ya no es válido')
      }
      const datos = await resp.json()
      guardarTokens(datos)
      return datos.accessToken
    })().finally(() => {
      refrescoEnCurso = null
    })
  }
  return refrescoEnCurso
}

/**
 * fetch con Bearer automático + UN reintento si el access token ya expiró
 * (401): refresca y repite la petición original una sola vez. Si el refresh
 * también falla, deja que el error (SesionExpiradaError) suba -- este
 * módulo no conoce rutas de React, es responsabilidad del caller (ver
 * useApi en AuthContext) reaccionar y mandar a /login.
 */
export async function apiFetch(path, opciones = {}) {
  const esFormData = opciones.body instanceof FormData

  const hacerRequest = (token) =>
    fetch(path, {
      ...opciones,
      headers: {
        ...(opciones.body && !esFormData ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(opciones.headers || {}),
      },
    })

  let resp = await hacerRequest(obtenerAccessToken())

  if (resp.status === 401) {
    const nuevoAccessToken = await refrescarTokens()
    resp = await hacerRequest(nuevoAccessToken)
  }

  return resp
}

/** Helper para los casos comunes: manda JSON y devuelve JSON ya parseado,
 * lanzando un Error legible con el "mensaje" que manda el backend si algo
 * sale mal (ver GlobalExceptionHandler/IdentidadExceptionHandler/MenuExceptionHandler). */
export async function apiFetchJson(path, opciones = {}) {
  const resp = await apiFetch(path, {
    ...opciones,
    body: opciones.body !== undefined ? JSON.stringify(opciones.body) : undefined,
  })
  const texto = await resp.text()
  const datos = texto ? JSON.parse(texto) : null
  if (!resp.ok) {
    throw new Error(datos?.mensaje || `Error ${resp.status}`)
  }
  return datos
}
