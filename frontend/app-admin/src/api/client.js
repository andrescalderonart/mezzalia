const CLAVE_ACCESS = 'mezzalia.accessToken'
const CLAVE_REFRESH = 'mezzalia.refreshToken'
const CLAVE_SUCURSAL = 'mezzalia.sucursalId'

export function guardarTokens({ accessToken, refreshToken }) {
  localStorage.setItem(CLAVE_ACCESS, accessToken)
  localStorage.setItem(CLAVE_REFRESH, refreshToken)
}

/**
 * Reemplaza SOLO el access token, sin tocar el refresh token -- uso
 * exclusivo del flujo de RF-10 PR4 (POST /api/admin/sesion/sucursal, ver
 * AccessTokenRespuesta en el backend): a proposito esa respuesta no trae
 * refreshToken, asi que reusar guardarTokens() aca pisaria el refresh
 * token valido que el cliente ya tiene con `undefined`.
 */
export function guardarAccessToken(accessToken) {
  localStorage.setItem(CLAVE_ACCESS, accessToken)
}

export function limpiarTokens() {
  localStorage.removeItem(CLAVE_ACCESS)
  localStorage.removeItem(CLAVE_REFRESH)
  limpiarSucursalSeleccionada()
}

export function obtenerAccessToken() {
  return localStorage.getItem(CLAVE_ACCESS)
}

export function obtenerRefreshToken() {
  return localStorage.getItem(CLAVE_REFRESH)
}

/**
 * RF-10 PR4: recuerda, solo en este navegador, la ultima sucursal que un
 * MESERO eligio para trabajar -- exclusivamente para poder reseleccionarla
 * SOLA (ver intentarSeleccionAutomatica abajo) despues de que un refresh
 * de por si se la borre del access token (limitacion conocida y
 * documentada de proposito, ver AuthService.emitirPar/misSucursalesDisponibles
 * en el backend). Nunca es la fuente de verdad de "que sucursales puede
 * elegir este usuario" -- eso siempre lo decide el backend.
 */
export function guardarSucursalSeleccionada(sucursalId) {
  localStorage.setItem(CLAVE_SUCURSAL, String(sucursalId))
}

export function obtenerSucursalSeleccionada() {
  return localStorage.getItem(CLAVE_SUCURSAL)
}

export function limpiarSucursalSeleccionada() {
  localStorage.removeItem(CLAVE_SUCURSAL)
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

/**
 * RF-10 PR4: intenta reseleccionar automaticamente, contra un access token
 * recien emitido, la misma sucursal que un MESERO ya tenia guardada en
 * este navegador (ver guardarSucursalSeleccionada arriba). Se usa tanto
 * despues de un refresh silencioso (el usuario ni se entera de que perdio
 * la sucursal por un instante) como despues de un login (si ya habia una
 * guardada de una sesion anterior en este mismo navegador, evita mostrarle
 * la pantalla de seleccion de nuevo).
 *
 * Devuelve el access token final a usar: el reseleccionado si funciono, o
 * el original recibido si no aplicaba (no era MESERO, o no habia ninguna
 * sucursal guardada todavia) o si la reseleccion fallo -- por ejemplo
 * porque el ADMIN_RESTAURANTE le quito esa sucursal mientras tanto (ver
 * AsignacionSucursalService.desasignar). NUNCA lanza: un fallo aca no
 * puede tumbar un login o un refresh que por lo demas fue exitoso -- en el
 * peor caso, el usuario simplemente termina viendo la pantalla de
 * seleccion manual.
 */
export async function intentarSeleccionAutomatica(accessTokenActual, requiereSeleccionSucursal) {
  if (!requiereSeleccionSucursal) return accessTokenActual
  const sucursalGuardada = obtenerSucursalSeleccionada()
  if (!sucursalGuardada) return accessTokenActual
  try {
    const resp = await fetch('/api/admin/sesion/sucursal', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessTokenActual}` },
      body: JSON.stringify({ sucursalId: Number(sucursalGuardada) }),
    })
    if (!resp.ok) {
      // Rechazo explicito del backend (ya no esta asignado, la sucursal se
      // desactivo, etc.) -- no vale la pena seguir insistiendo con este id
      // en cada refresh futuro, mejor que el proximo intento de acceder a
      // Mesas/Pedidos lo mande derecho a elegir de nuevo.
      limpiarSucursalSeleccionada()
      return accessTokenActual
    }
    const datos = await resp.json()
    guardarAccessToken(datos.accessToken)
    return datos.accessToken
  } catch {
    // Fallo de red u otro problema transitorio: a proposito NO borra la
    // sucursal guardada (a diferencia del rechazo explicito de arriba) --
    // vale la pena reintentar en el proximo refresh en vez de forzar una
    // pantalla de seleccion por un corte de red pasajero.
    return accessTokenActual
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
      // RF-10 PR4: un MESERO SIEMPRE pierde el claim sucursalId en cada
      // refresh (ver TokensRespuesta.requiereSeleccionSucursal) -- intenta
      // recuperarla en el acto con la ultima que tenia guardada, sin que
      // el resto de la app (ni el usuario) note la diferencia.
      return await intentarSeleccionAutomatica(datos.accessToken, datos.requiereSeleccionSucursal)
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
    const error = new Error(datos?.mensaje || `Error ${resp.status}`)
    // `status` y `codigo` (este ultimo solo presente en el 409 de
    // SucursalNoSeleccionadaException, ver SucursalExceptionHandler) le
    // permiten al caller reaccionar por CODIGO en vez de parsear el texto
    // de "mensaje" -- ver useApiJson en AuthContext.jsx, que redirige a
    // /seleccionar-sucursal cuando ve este codigo especifico.
    error.status = resp.status
    error.codigo = datos?.codigo
    throw error
  }
  return datos
}
