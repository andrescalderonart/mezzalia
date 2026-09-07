const CLAVE_ACCESS = 'mezzalia.accessToken'
const CLAVE_REFRESH = 'mezzalia.refreshToken'
// RF-10 PR4: ultima sucursal que este navegador vio elegir a un MESERO --
// se guarda SOLO para poder reseleccionarla automaticamente (sin volver a
// preguntarle) cada vez que el backend indica requiereSeleccionSucursal
// (login o refresh). Nunca es la fuente de verdad: el backend revalida en
// cada llamada real que esa sucursal le siga siendo valida a ESTE usuario
// (ver AuthService.seleccionarSucursal) -- si otro usuario loguea despues
// en el mismo navegador y esta id ya no le pertenece, la reseleccion
// automatica simplemente falla en silencio (ver intentarReseleccionAutomatica)
// y queda pendiente de que la elija a mano en /seleccionar-sucursal.
const CLAVE_SUCURSAL = 'mezzalia.ultimaSucursalId'

// Notificado cada vez que el access token cambia POR FUERA de login()
// (refresh silencioso disparado por apiFetch, o una seleccion/reseleccion
// de sucursal) -- AuthContext se suscribe a esto para poder re-derivar
// `usuario` (incluido el claim sucursalId) del token nuevo. Sin este aviso,
// el estado de React quedaria mostrando el sucursalId de un token que el
// localStorage ya reemplazo.
let notificarCambioDeAccessToken = () => {}
export function alCambiarAccessToken(callback) {
  notificarCambioDeAccessToken = callback
}

export function guardarTokens({ accessToken, refreshToken }) {
  localStorage.setItem(CLAVE_ACCESS, accessToken)
  localStorage.setItem(CLAVE_REFRESH, refreshToken)
  notificarCambioDeAccessToken()
}

// RF-10 PR4: POST /api/admin/sesion/sucursal re-emite SOLO el access token
// (ver AccessTokenRespuesta en el backend) -- el refresh token vigente no
// se toca, a proposito.
export function actualizarAccessToken(accessToken) {
  localStorage.setItem(CLAVE_ACCESS, accessToken)
  notificarCambioDeAccessToken()
}

export function limpiarTokens() {
  localStorage.removeItem(CLAVE_ACCESS)
  localStorage.removeItem(CLAVE_REFRESH)
  localStorage.removeItem(CLAVE_SUCURSAL)
}

export function obtenerAccessToken() {
  return localStorage.getItem(CLAVE_ACCESS)
}

export function obtenerRefreshToken() {
  return localStorage.getItem(CLAVE_REFRESH)
}

export function guardarUltimaSucursalId(sucursalId) {
  localStorage.setItem(CLAVE_SUCURSAL, String(sucursalId))
}

export function obtenerUltimaSucursalId() {
  const valor = localStorage.getItem(CLAVE_SUCURSAL)
  return valor ? Number(valor) : null
}

/**
 * Decodifica el payload de un JWT SIN verificar la firma -- el navegador no
 * tiene (ni debe tener) el JWT_SECRET. Esto es solo para leer claims
 * públicas (username, rol, tenantId, sucursalId) y pintar la UI (nombre de
 * usuario, qué botones mostrar según el rol). La verificación real de la
 * firma la hace SIEMPRE el backend en cada request (JwtAuthenticationFilter)
 * -- nunca usar este decode para decisiones de seguridad, solo de interfaz.
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

// RF-10 PR4: distinto de SesionExpiradaError -- el usuario SIGUE
// autenticado, solo le falta el paso de elegir sucursal de trabajo antes de
// que Mesas/Pedidos/Carta lo dejen operar (ver sucursal.SucursalContext y
// "sucursalActual()" en el backend). apiFetchJson lo lanza cuando el 409
// trae el campo "codigo": "SUCURSAL_NO_SELECCIONADA" (ver
// SucursalExceptionHandler), y useApiJson en AuthContext.jsx reacciona
// mandando al usuario a /seleccionar-sucursal en vez de solo mostrarle el
// mensaje crudo del backend.
export class SucursalNoSeleccionadaError extends Error {}

/**
 * RF-10 PR4 (ver AuthService.emitirPar/seleccionarSucursal, y el javadoc
 * largo de la limitacion conocida ahi): tanto login() como un refresh
 * exitoso devuelven requiereSeleccionSucursal=true para un MESERO, incluso
 * si ya habia elegido sucursal antes de que el access token expirara --
 * porque esa eleccion vive solo en el access token, no en el refresh token.
 * En vez de forzar al mesero a re-elegir a mano cada vez que su token
 * expira durante un turno, si ya sabemos cual eligio la ultima vez en este
 * navegador, se la volvemos a pedir al backend de una. El backend igual
 * revalida que le siga siendo valida a este usuario (reasignado a otra
 * sucursal, desactivada, etc.) -- si la llamada falla o no hay ninguna
 * guardada, esta funcion NO lanza ni bloquea nada: el access token
 * simplemente se queda sin el claim sucursalId, y el gate de
 * RequiereSucursal.jsx (o, si ya estaba en una pantalla que lo necesitaba,
 * el 409 SUCURSAL_NO_SELECCIONADA que atrapa useApiJson) lo va a mandar a
 * elegir de nuevo la proxima vez que haga falta.
 */
export async function intentarReseleccionAutomatica(datosLoginORefresh) {
  if (!datosLoginORefresh?.requiereSeleccionSucursal) return
  const ultimaSucursalId = obtenerUltimaSucursalId()
  if (ultimaSucursalId == null) return
  try {
    const resp = await fetch('/api/admin/sesion/sucursal', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${datosLoginORefresh.accessToken}`,
      },
      body: JSON.stringify({ sucursalId: ultimaSucursalId }),
    })
    if (!resp.ok) return
    const datos = await resp.json()
    actualizarAccessToken(datos.accessToken)
  } catch {
    // Sin red, backend caido, etc. -- ver el comentario de arriba: no vale
    // la pena romper un login/refresh que sí funcionó por esto.
  }
}

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
      await intentarReseleccionAutomatica(datos)
      // No devolver datos.accessToken directo: si la reselección de arriba
      // tuvo éxito, actualizarAccessToken ya puso uno más nuevo (con
      // sucursalId) en localStorage, y es ese el que apiFetch debe usar
      // para reintentar la petición que disparó este refresh.
      return obtenerAccessToken()
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
 * sale mal (ver GlobalExceptionHandler/IdentidadExceptionHandler/MenuExceptionHandler).
 * Un 409 con "codigo": "SUCURSAL_NO_SELECCIONADA" (ver SucursalExceptionHandler)
 * lanza SucursalNoSeleccionadaError en vez del Error genérico, para que
 * useApiJson (AuthContext.jsx) lo pueda distinguir y reaccionar. */
export async function apiFetchJson(path, opciones = {}) {
  const resp = await apiFetch(path, {
    ...opciones,
    body: opciones.body !== undefined ? JSON.stringify(opciones.body) : undefined,
  })
  const texto = await resp.text()
  const datos = texto ? JSON.parse(texto) : null
  if (!resp.ok) {
    if (datos?.codigo === 'SUCURSAL_NO_SELECCIONADA') {
      throw new SucursalNoSeleccionadaError(datos.mensaje)
    }
    throw new Error(datos?.mensaje || `Error ${resp.status}`)
  }
  return datos
}
