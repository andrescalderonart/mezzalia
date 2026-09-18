import { useEffect, useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { useAuth, useApiJson } from '../context/AuthContext.jsx'

// En produccion, app-cliente vive en un dominio propio -- VITE_APP_CLIENTE_URL
// es obligatorio ahi y siempre gana si esta seteado. En dev, en vez de
// depender de una IP de LAN hardcodeada en .env.local (que queda stale
// cada vez que el DHCP de la red le reasigna otra IP a esta laptop -- ya
// paso una vez, ver feedback_git_mounted_folder / historial de esta
// sesion), derivamos el host directo de donde se cargo ESTE panel: si
// abriste el admin en http://192.168.2.X:5173, el QR sale apuntando a esa
// misma IP en el puerto 5174 -- coherente sin configuracion manual. Si lo
// abres por localhost, el QR tambien sale en localhost (correcto para vos,
// inutil para un celular real -- para eso, abrí el panel usando la IP de
// LAN, no localhost).
const PUERTO_DEV_APP_CLIENTE = '5174'
const URL_APP_CLIENTE =
  import.meta.env.VITE_APP_CLIENTE_URL ||
  `${window.location.protocol}//${window.location.hostname}:${PUERTO_DEV_APP_CLIENTE}`

const formatoHora = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit' })

export default function MesasPage() {
  const { usuario } = useAuth()
  const apiJson = useApiJson()
  const puedeEditar = usuario?.rol === 'ADMIN_RESTAURANTE'

  const [mesas, setMesas] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [codigoNuevo, setCodigoNuevo] = useState('')
  const [creando, setCreando] = useState(false)

  // RF-10: toda mesa nueva necesita una sucursal activa del tenant. La
  // inmensa mayoria de restaurantes tiene una sola sucursal (ver
  // SucursalesPage.jsx) -- en ese caso se preselecciona sola y no hay
  // combo que mostrar; con 2+ sucursales activas el combo es obligatorio
  // porque no hay forma correcta de adivinar cual quiso el usuario.
  const [sucursales, setSucursales] = useState([])
  const [sucursalId, setSucursalId] = useState('')

  // sesiones[mesaId] = { token, expiraEn }. Se hidrata en cargarMesas()
  // desde `mesa.sesionActiva` (GET /api/admin/mesas ya la incluye, ver
  // MesaAdminController.listarMesas) para que un refresh de página no
  // muestre "sin QR" en una mesa que ya tiene uno impreso y vigente --
  // antes de esto, este estado SOLO se llenaba con lo generado en la
  // misma pestaña del navegador, y no había forma de recuperarlo sin
  // regenerar (lo que invalida el QR físico ya pegado en la mesa, ver
  // MesaService.generarSesion).
  const [sesiones, setSesiones] = useState({})
  const [generandoId, setGenerandoId] = useState(null)
  const [mesaImprimiendo, setMesaImprimiendo] = useState(null)

  async function cargarMesas() {
    setCargando(true)
    setError('')
    try {
      const datos = await apiJson('/api/admin/mesas')
      setMesas(datos)
      setSesiones((prev) => {
        const hidratadas = { ...prev }
        datos.forEach((mesa) => {
          if (mesa.sesionActiva) {
            hidratadas[mesa.id] = mesa.sesionActiva
          }
        })
        return hidratadas
      })
    } catch (e) {
      setError(e.message)
    } finally {
      setCargando(false)
    }
  }

  async function cargarSucursales() {
    try {
      const datos = await apiJson('/api/admin/sucursales')
      setSucursales(datos)
      // Preselecciona la unica sucursal activa; con varias, deja el combo
      // vacio a proposito para forzar una eleccion explicita.
      if (datos.length === 1) {
        setSucursalId(String(datos[0].id))
      }
    } catch (e) {
      // No bloquea la carga de mesas si esto falla -- el formulario de
      // creacion simplemente queda deshabilitado (ver puedeCrear abajo).
      setError(e.message)
    }
  }

  useEffect(() => {
    cargarMesas()
    cargarSucursales()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function onCrearMesa(e) {
    e.preventDefault()
    if (!codigoNuevo.trim() || !sucursalId) return
    setCreando(true)
    setError('')
    try {
      const mesa = await apiJson('/api/admin/mesas', {
        method: 'POST',
        body: { codigo: codigoNuevo.trim(), sucursalId: Number(sucursalId) },
      })
      setCodigoNuevo('')
      setMesas((prev) => [...prev, mesa])
    } catch (e) {
      setError(e.message)
    } finally {
      setCreando(false)
    }
  }

  async function onDesactivarMesa(mesa) {
    if (!window.confirm(`¿Desactivar "${mesa.codigo}"? El QR que tenga impreso dejará de servir la carta.`)) return
    setError('')
    try {
      await apiJson(`/api/admin/mesas/${mesa.id}`, { method: 'DELETE' })
      setMesas((prev) => prev.filter((m) => m.id !== mesa.id))
    } catch (e) {
      setError(e.message)
    }
  }

  async function onGenerarSesion(mesa) {
    if (
      sesiones[mesa.id] &&
      !window.confirm('Ya hay un QR vigente para esta mesa. Regenerarlo invalida el que esté impreso -- ¿seguro?')
    ) {
      return
    }
    setGenerandoId(mesa.id)
    setError('')
    try {
      const sesion = await apiJson(`/api/admin/mesas/${mesa.id}/sesion`, { method: 'POST' })
      setSesiones((prev) => ({ ...prev, [mesa.id]: sesion }))
    } catch (e) {
      setError(e.message)
    } finally {
      setGenerandoId(null)
    }
  }

  function onImprimir(mesa) {
    setMesaImprimiendo(mesa)
    // El DOM de la zona de impresión se actualiza en el próximo render;
    // sin este pequeño respiro, window.print() puede disparar con el
    // contenido todavía viejo (o vacío) dentro de #zona-impresion.
    requestAnimationFrame(() => window.print())
  }

  const sesionImprimiendo = mesaImprimiendo ? sesiones[mesaImprimiendo.id] : null
  const urlQrImprimiendo = sesionImprimiendo
    ? `${URL_APP_CLIENTE}/carta/${sesionImprimiendo.token}`
    : null

  return (
    <div>
      <div className="flex items-center justify-between mb-6 print:hidden">
        <div>
          <h1 className="font-display text-2xl text-crema">Mesas</h1>
          <p className="text-sm text-crema/40">
            Genera el código QR que cada mesa usa para ver la carta sin iniciar sesión.
          </p>
        </div>
      </div>

      {error && (
        <div className="mb-4 text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2 animate-fadeInUp print:hidden">
          {error}
        </div>
      )}

      {puedeEditar && sucursales.length === 0 && !cargando && (
        <div className="mb-4 text-sm text-crema/40 bg-carbon-800/60 border border-carbon-700 rounded-lg px-3 py-2 print:hidden">
          Todavía no hay ninguna sucursal activa -- creá una en{' '}
          <a href="/sucursales" className="underline text-dorado">Sucursales</a> antes de poder agregar mesas.
        </div>
      )}

      {puedeEditar && sucursales.length > 0 && (
        <form onSubmit={onCrearMesa} className="card p-4 mb-6 flex flex-col sm:flex-row gap-2 print:hidden">
          <input
            className="input-carbon flex-1"
            placeholder="Nombre o número de mesa (ej. Mesa 4, Terraza 2)"
            value={codigoNuevo}
            onChange={(e) => setCodigoNuevo(e.target.value)}
          />
          {sucursales.length > 1 && (
            <select
              className="input-carbon sm:w-48"
              value={sucursalId}
              onChange={(e) => setSucursalId(e.target.value)}
            >
              <option value="">Elegí una sucursal…</option>
              {sucursales.map((s) => (
                <option key={s.id} value={s.id}>{s.nombre}</option>
              ))}
            </select>
          )}
          <button type="submit" disabled={creando || !sucursalId} className="btn-primario sm:w-auto">
            {creando ? 'Creando…' : '+ Nueva mesa'}
          </button>
        </form>
      )}

      {cargando ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 print:hidden">
          {[1, 2, 3].map((n) => (
            <div key={n} className="h-48 rounded-2xl bg-carbon-800 animate-pulseSoft" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 print:hidden">
          {mesas.map((mesa) => {
            const sesion = sesiones[mesa.id]
            const urlCarta = sesion ? `${URL_APP_CLIENTE}/carta/${sesion.token}` : null
            return (
              <div key={mesa.id} className="card p-4 animate-fadeInUp">
                <div className="flex items-start justify-between gap-2 mb-3">
                  <div>
                    <h3 className="font-medium text-crema">{mesa.codigo}</h3>
                    {/* Solo vale la pena mostrar la sucursal cuando hay mas
                        de una -- con una sola es ruido visual obvio. */}
                    {sucursales.length > 1 && (
                      <p className="text-[11px] text-crema/40">
                        {sucursales.find((s) => s.id === mesa.sucursalId)?.nombre || 'Sucursal desconocida'}
                      </p>
                    )}
                  </div>
                  {puedeEditar && (
                    <button onClick={() => onDesactivarMesa(mesa)} className="text-[11px] text-crema/40 hover:text-red-300 transition-colors duration-150">
                      Desactivar
                    </button>
                  )}
                </div>

                {sesion ? (
                  <div className="flex flex-col items-center gap-2">
                    <div className="bg-crema rounded-lg p-2">
                      <QRCodeSVG value={urlCarta} size={128} fgColor="#0d0c0b" bgColor="#f3ecdf" marginSize={1} level="M" />
                    </div>
                    <p className="text-[11px] text-crema/40 text-center">
                      Válido hasta las {formatoHora.format(new Date(sesion.expiraEn))}
                    </p>
                    <div className="flex gap-2 w-full">
                      <button onClick={() => onImprimir(mesa)} className="btn-secundario flex-1 text-xs py-2">
                        Imprimir
                      </button>
                      {puedeEditar && (
                        <button
                          onClick={() => onGenerarSesion(mesa)}
                          disabled={generandoId === mesa.id}
                          className="btn-secundario flex-1 text-xs py-2"
                        >
                          {generandoId === mesa.id ? '…' : 'Regenerar'}
                        </button>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="flex flex-col items-center gap-3 py-4">
                    <p className="text-xs text-crema/40 text-center">Esta mesa todavía no tiene un código QR activo.</p>
                    {puedeEditar && (
                      <button
                        onClick={() => onGenerarSesion(mesa)}
                        disabled={generandoId === mesa.id}
                        className="btn-primario w-full text-xs py-2"
                      >
                        {generandoId === mesa.id ? 'Generando…' : 'Generar código QR'}
                      </button>
                    )}
                  </div>
                )}
              </div>
            )
          })}

          {mesas.length === 0 && (
            <div className="col-span-full text-sm text-crema/40 py-10 text-center">
              Todavía no hay mesas creadas.
            </div>
          )}
        </div>
      )}

      {/* Zona de impresión: invisible en pantalla, solo aparece con
          window.print() gracias a las clases print:*. Se mantiene siempre
          en el DOM (aunque vacía) porque cambiar de "no existe" a "existe"
          justo antes de imprimir es más frágil que solo llenarla de datos. */}
      <div id="zona-impresion" className="hidden print:flex print:fixed print:inset-0 print:flex-col print:items-center print:justify-center print:gap-4">
        {mesaImprimiendo && sesionImprimiendo && (
          <>
            <div className="text-2xl font-display">Mezzalia</div>
            <div className="text-lg">{mesaImprimiendo.codigo}</div>
            <QRCodeSVG value={urlQrImprimiendo} size={280} fgColor="#0d0c0b" bgColor="#ffffff" marginSize={2} level="M" />
            <div className="text-sm text-center max-w-xs">Escanea para ver la carta y pedir desde tu celular</div>
          </>
        )}
      </div>
    </div>
  )
}
