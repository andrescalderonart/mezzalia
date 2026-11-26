import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  apiFetch,
  apiFetchJson,
  decodificarJwt,
  guardarTokens,
  guardarUltimaSucursalId,
  intentarReseleccionAutomatica,
  obtenerAccessToken,
  obtenerRefreshToken,
  obtenerUltimaSucursalId,
  SesionExpiradaError,
  SucursalNoSeleccionadaError,
} from './client.js'
import { jwtFalso } from '../test/jwt.js'

const respuesta = (status, cuerpo) =>
  new Response(cuerpo === undefined ? '' : JSON.stringify(cuerpo), { status })

beforeEach(() => {
  globalThis.fetch = vi.fn()
})

describe('decodificarJwt', () => {
  it('lee los claims, incluidos acentos (UTF-8 en base64url)', () => {
    const token = jwtFalso({ sub: 'josé.muñoz', rol: 'MESERO', sucursalId: 3 })
    expect(decodificarJwt(token)).toEqual({ sub: 'josé.muñoz', rol: 'MESERO', sucursalId: 3 })
  })

  it('devuelve null con un token mal formado en vez de lanzar', () => {
    expect(decodificarJwt('esto-no-es-un-jwt')).toBeNull()
    expect(decodificarJwt('a.%%%.c')).toBeNull()
  })
})

describe('apiFetch', () => {
  it('manda el access token como Bearer', async () => {
    guardarTokens({ accessToken: 'acc-1', refreshToken: 'ref-1' })
    fetch.mockResolvedValueOnce(respuesta(200, {}))

    await apiFetch('/api/admin/mesas')

    expect(fetch).toHaveBeenCalledWith('/api/admin/mesas', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer acc-1' }),
    }))
  })

  it('ante un 401 refresca UNA vez y repite la peticion con el token nuevo', async () => {
    guardarTokens({ accessToken: 'viejo', refreshToken: 'ref-1' })
    fetch
      .mockResolvedValueOnce(respuesta(401))
      .mockResolvedValueOnce(respuesta(200, { accessToken: 'nuevo', refreshToken: 'ref-2' }))
      .mockResolvedValueOnce(respuesta(200, { ok: true }))

    const resp = await apiFetch('/api/admin/pedidos')

    expect(resp.status).toBe(200)
    expect(fetch).toHaveBeenCalledTimes(3)
    expect(fetch.mock.calls[1][0]).toBe('/api/auth/refresh')
    expect(fetch.mock.calls[2][1].headers.Authorization).toBe('Bearer nuevo')
    expect(obtenerRefreshToken()).toBe('ref-2')
  })

  it('varios 401 simultaneos comparten UN solo refresh (no rotan el refresh token varias veces)', async () => {
    guardarTokens({ accessToken: 'viejo', refreshToken: 'ref-1' })
    fetch.mockImplementation(async (url, opciones) => {
      if (url === '/api/auth/refresh') return respuesta(200, { accessToken: 'nuevo', refreshToken: 'ref-2' })
      return opciones.headers.Authorization === 'Bearer nuevo' ? respuesta(200, {}) : respuesta(401)
    })

    await Promise.all([apiFetch('/a'), apiFetch('/b'), apiFetch('/c')])

    const refrescos = fetch.mock.calls.filter(([url]) => url === '/api/auth/refresh')
    expect(refrescos).toHaveLength(1)
  })

  it('si el refresh falla, limpia la sesion y lanza SesionExpiradaError', async () => {
    guardarTokens({ accessToken: 'viejo', refreshToken: 'revocado' })
    guardarUltimaSucursalId(7)
    fetch.mockResolvedValueOnce(respuesta(401)).mockResolvedValueOnce(respuesta(401))

    await expect(apiFetch('/api/admin/mesas')).rejects.toBeInstanceOf(SesionExpiradaError)
    expect(obtenerAccessToken()).toBeNull()
    expect(obtenerRefreshToken()).toBeNull()
    expect(obtenerUltimaSucursalId()).toBeNull()
  })

  it('sin refresh token guardado, un 401 lanza SesionExpiradaError sin llamar al backend', async () => {
    fetch.mockResolvedValueOnce(respuesta(401))

    await expect(apiFetch('/api/admin/mesas')).rejects.toBeInstanceOf(SesionExpiradaError)
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('no fuerza Content-Type JSON cuando el body es FormData (subida de fotos)', async () => {
    fetch.mockResolvedValueOnce(respuesta(200, {}))
    const form = new FormData()
    form.append('imagen', new Blob(['x']), 'plato.jpg')

    await apiFetch('/api/admin/menu/items/1/imagen', { method: 'POST', body: form })

    expect(fetch.mock.calls[0][1].headers['Content-Type']).toBeUndefined()
  })
})

describe('apiFetchJson', () => {
  it('serializa el body y devuelve el JSON parseado', async () => {
    fetch.mockResolvedValueOnce(respuesta(201, { id: 5 }))

    const datos = await apiFetchJson('/api/admin/mesas', { method: 'POST', body: { nombre: 'Mesa 1' } })

    expect(datos).toEqual({ id: 5 })
    expect(fetch.mock.calls[0][1].body).toBe('{"nombre":"Mesa 1"}')
    expect(fetch.mock.calls[0][1].headers['Content-Type']).toBe('application/json')
  })

  it('devuelve null con una respuesta vacia (204)', async () => {
    fetch.mockResolvedValueOnce(new Response(null, { status: 204 }))
    expect(await apiFetchJson('/api/admin/mesas/1', { method: 'DELETE' })).toBeNull()
  })

  it('un 409 SUCURSAL_NO_SELECCIONADA lanza SucursalNoSeleccionadaError (lo usa useApiJson para redirigir)', async () => {
    fetch.mockResolvedValueOnce(respuesta(409, { codigo: 'SUCURSAL_NO_SELECCIONADA', mensaje: 'Elegí sucursal' }))

    const error = await apiFetchJson('/api/admin/mesas').catch((e) => e)

    expect(error).toBeInstanceOf(SucursalNoSeleccionadaError)
    expect(error.message).toBe('Elegí sucursal')
  })

  it('otros errores lanzan Error con el mensaje del backend, o el codigo HTTP si no hay mensaje', async () => {
    fetch.mockResolvedValueOnce(respuesta(400, { mensaje: 'Nombre obligatorio' }))
    await expect(apiFetchJson('/x')).rejects.toThrow('Nombre obligatorio')

    fetch.mockResolvedValueOnce(respuesta(500))
    await expect(apiFetchJson('/x')).rejects.toThrow('Error 500')
  })
})

describe('intentarReseleccionAutomatica', () => {
  it('no hace nada si el backend no pide seleccionar sucursal', async () => {
    guardarUltimaSucursalId(3)
    await intentarReseleccionAutomatica({ accessToken: 't', requiereSeleccionSucursal: false })
    expect(fetch).not.toHaveBeenCalled()
  })

  it('no hace nada si este navegador nunca vio elegir sucursal', async () => {
    await intentarReseleccionAutomatica({ accessToken: 't', requiereSeleccionSucursal: true })
    expect(fetch).not.toHaveBeenCalled()
  })

  it('re-elige la ultima sucursal y guarda el access token nuevo', async () => {
    guardarUltimaSucursalId(3)
    fetch.mockResolvedValueOnce(respuesta(200, { accessToken: 'con-sucursal' }))

    await intentarReseleccionAutomatica({ accessToken: 'sin-sucursal', requiereSeleccionSucursal: true })

    expect(fetch).toHaveBeenCalledWith('/api/admin/sesion/sucursal', expect.objectContaining({
      body: JSON.stringify({ sucursalId: 3 }),
      headers: expect.objectContaining({ Authorization: 'Bearer sin-sucursal' }),
    }))
    expect(obtenerAccessToken()).toBe('con-sucursal')
  })

  it('si el backend rechaza la sucursal (ya no le pertenece), falla en silencio sin tocar el token', async () => {
    localStorage.setItem('mezzalia.accessToken', 'sin-sucursal')
    guardarUltimaSucursalId(3)
    fetch.mockResolvedValueOnce(respuesta(403, { mensaje: 'No asignado' }))

    await expect(
      intentarReseleccionAutomatica({ accessToken: 'sin-sucursal', requiereSeleccionSucursal: true }),
    ).resolves.toBeUndefined()
    expect(obtenerAccessToken()).toBe('sin-sucursal')
  })

  it('sin red tampoco rompe el login/refresh', async () => {
    guardarUltimaSucursalId(3)
    fetch.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    await expect(
      intentarReseleccionAutomatica({ accessToken: 't', requiereSeleccionSucursal: true }),
    ).resolves.toBeUndefined()
  })
})
