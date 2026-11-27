import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApiJson } from '../../context/AuthContext.jsx'
import { idsNuevos, minutosDesde, ordenarParaCocina, urgencia } from './logica.js'
import { prepararAudio, sonarPedidoNuevo } from './sonido.js'

/**
 * Vista de cocina ("KDS-lite"), auditoría 2026-09-08 §3.1 / paso 7.
 *
 * No es el KDS de Fase 4 (pantallas dedicadas, impresoras de comanda): es
 * una URL más del panel, pensada para dejarla abierta en una tablet o un
 * monitor viejo en la cocina. Reusa el mismo endpoint y el mismo polling que
 * PedidosPage, sin backend nuevo. El mesero deja de ser el mensajero entre
 * la mesa y la cocina.
 *
 * Decisiones:
 * - Pide al backend SOLO los estados que le importan a cocina
 *   (`?estado=RECIBIDO` y `?estado=EN_PREPARACION`), no la lista completa:
 *   GET /api/admin/pedidos sin filtro devuelve todo el historial del tenant
 *   (con N+1 de items/comensal por pedido), y esta pantalla consulta cada
 *   pocos segundos durante todo el servicio.
 * - Sin precios ni datos del comensal: la cocina no los necesita, y el
 *   teléfono es dato personal (Habeas Data, §10). Lo que se muestra en
 *   grande es lo que hay que cocinar y las notas ("sin cebolla").
 * - Orden FIFO (lo más viejo primero) y el tiempo de espera en color.
 * - Fuera de <Layout> (pantalla completa, letra grande, sin sidebar),
 *   pero dentro de ProtectedRoute/RutaConRol/RequiereSucursal: un MESERO
 *   sin sucursal elegida vería pedidos de... ninguna, el backend le daría 409.
 * - El reloj de "hace N min" usa la hora del navegador contra `creadoEn`
 *   del servidor: si los relojes difieren, el conteo se corre. Aceptable
 *   para un indicador visual; minutosDesde() nunca devuelve negativos.
 */

// Igual que PedidosPage: sin WebSocket todavía. Ver el comentario de
// INTERVALO_POLLING_MS ahí sobre por qué este orden de magnitud.
const INTERVALO_POLLING_MS = 6000
// Cada cuánto se re-pinta el "hace N min" aunque no lleguen datos nuevos.
const INTERVALO_RELOJ_MS = 30000

const COLUMNAS = [
  {
    estado: 'RECIBIDO',
    titulo: 'Nuevos',
    vacio: 'Sin pedidos nuevos.',
    siguiente: 'EN_PREPARACION',
    accion: 'Empezar',
    estiloBoton: 'bg-dorado text-carbon-950 hover:bg-dorado-light',
  },
  {
    estado: 'EN_PREPARACION',
    titulo: 'En preparación',
    vacio: 'Nada en preparación.',
    siguiente: 'LISTO',
    accion: '¡Listo!',
    estiloBoton: 'bg-green-500 text-carbon-950 hover:bg-green-400',
  },
]

const ESTILO_URGENCIA = {
  normal: 'text-crema/60',
  atencion: 'text-dorado font-semibold',
  urgente: 'text-red-300 font-bold animate-pulseSoft',
}

const BORDE_URGENCIA = {
  normal: 'border-carbon-700',
  atencion: 'border-dorado/50',
  urgente: 'border-red-500/70',
}

export default function CocinaPage() {
  const apiJson = useApiJson()
  const [pedidos, setPedidos] = useState({ RECIBIDO: [], EN_PREPARACION: [] })
  const [mesasPorId, setMesasPorId] = useState({})
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [actualizandoId, setActualizandoId] = useState(null)
  const [sonidoActivo, setSonidoActivo] = useState(false)
  const [ahora, setAhora] = useState(() => Date.now())

  const peticionIdRef = useRef(0)
  // null hasta la primera carga: ver idsNuevos() -- no suena al abrir.
  const idsConocidosRef = useRef(null)
  const sonidoActivoRef = useRef(false)
  sonidoActivoRef.current = sonidoActivo

  const cargar = useCallback(async () => {
    const idPeticion = ++peticionIdRef.current
    try {
      const [recibidos, enPreparacion] = await Promise.all([
        apiJson('/api/admin/pedidos?estado=RECIBIDO'),
        apiJson('/api/admin/pedidos?estado=EN_PREPARACION'),
      ])
      if (idPeticion !== peticionIdRef.current) return

      const nuevos = idsNuevos(idsConocidosRef.current, recibidos)
      if (nuevos.length > 0 && sonidoActivoRef.current) sonarPedidoNuevo()
      idsConocidosRef.current = new Set([...recibidos, ...enPreparacion].map((p) => p.id))

      setPedidos({ RECIBIDO: ordenarParaCocina(recibidos), EN_PREPARACION: ordenarParaCocina(enPreparacion) })
      setAhora(Date.now())
      setError('')
    } catch (e) {
      if (idPeticion !== peticionIdRef.current) return
      setError(e.message)
    } finally {
      if (idPeticion === peticionIdRef.current) setCargando(false)
    }
  }, [apiJson])

  useEffect(() => {
    apiJson('/api/admin/mesas')
      .then((mesas) => setMesasPorId(Object.fromEntries(mesas.map((m) => [m.id, m.codigo]))))
      .catch(() => {
        // Igual que PedidosPage: sin nombres de mesa se muestra "Mesa #id".
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    cargar()
    const polling = setInterval(cargar, INTERVALO_POLLING_MS)
    const reloj = setInterval(() => setAhora(Date.now()), INTERVALO_RELOJ_MS)
    return () => {
      clearInterval(polling)
      clearInterval(reloj)
    }
  }, [cargar])

  async function avanzar(pedido, nuevoEstado) {
    setActualizandoId(pedido.id)
    setError('')
    try {
      await apiJson(`/api/admin/pedidos/${pedido.id}/estado`, { method: 'PATCH', body: { estado: nuevoEstado } })
      // Recargar en vez de mover a mano: si otro dispositivo (el mesero)
      // también cambió el pedido, la cocina ve el estado real.
      await cargar()
    } catch (e) {
      setError(e.message)
    } finally {
      setActualizandoId(null)
    }
  }

  function alternarSonido() {
    if (sonidoActivo) {
      setSonidoActivo(false)
      return
    }
    if (prepararAudio()) {
      setSonidoActivo(true)
      sonarPedidoNuevo() // confirma que se oye
    } else {
      setError('Este navegador no permite reproducir sonido.')
    }
  }

  const nombreMesa = (mesaId) => mesasPorId[mesaId] ?? `Mesa #${mesaId}`

  return (
    <div className="min-h-dvh bg-carbon-950 text-crema p-4 md:p-6">
      <header className="flex flex-wrap items-center justify-between gap-3 mb-5">
        <div className="flex items-center gap-4">
          <Link to="/pedidos" className="text-sm text-crema/50 hover:text-crema">
            ← Panel
          </Link>
          <h1 className="font-display font-semibold text-3xl">Cocina</h1>
        </div>
        <button
          onClick={alternarSonido}
          aria-pressed={sonidoActivo}
          className={`text-sm px-4 py-2 rounded-full border transition-colors duration-150 ${
            sonidoActivo ? 'bg-dorado/15 border-dorado/40 text-dorado' : 'bg-carbon-800 border-carbon-600 text-crema/70'
          }`}
        >
          {sonidoActivo ? '🔔 Sonido activado' : '🔕 Activar sonido'}
        </button>
      </header>

      {error && (
        <div role="alert" className="mb-4 text-base text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-4 py-3">
          {error}
        </div>
      )}

      {/* Dos columnas desde tablet vertical (768px): en cocina hay que ver
          "Nuevos" y "En preparación" a la vez sin hacer scroll. */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 lg:gap-5">
        {COLUMNAS.map((col) => (
          <section key={col.estado} aria-label={col.titulo}>
            <h2 className="text-xl font-semibold mb-3 flex items-center gap-2">
              {col.titulo}
              <span className="text-sm font-normal text-crema/50 bg-carbon-800 rounded-full px-2.5 py-0.5">
                {pedidos[col.estado].length}
              </span>
            </h2>

            {cargando ? (
              <div className="h-40 rounded-2xl bg-carbon-800 animate-pulseSoft" />
            ) : pedidos[col.estado].length === 0 ? (
              <p className="text-crema/40 text-lg py-10 text-center border border-dashed border-carbon-700 rounded-2xl">
                {col.vacio}
              </p>
            ) : (
              <div className="space-y-4">
                {pedidos[col.estado].map((pedido) => (
                  <TarjetaCocina
                    key={pedido.id}
                    pedido={pedido}
                    nombreMesa={nombreMesa(pedido.mesaId)}
                    minutos={minutosDesde(pedido.creadoEn, ahora)}
                    accion={col.accion}
                    estiloBoton={col.estiloBoton}
                    actualizando={actualizandoId === pedido.id}
                    onAvanzar={() => avanzar(pedido, col.siguiente)}
                  />
                ))}
              </div>
            )}
          </section>
        ))}
      </div>
    </div>
  )
}

function TarjetaCocina({ pedido, nombreMesa, minutos, accion, estiloBoton, actualizando, onAvanzar }) {
  const nivel = urgencia(minutos)
  return (
    <article className={`rounded-2xl bg-carbon-900 border-2 ${BORDE_URGENCIA[nivel]} p-4`} data-urgencia={nivel}>
      <div className="flex items-baseline justify-between gap-3 mb-3">
        <h3 className="text-2xl font-semibold">
          {nombreMesa}
          {/* "Pedido de la mesa": a quién va el plato. */}
          {pedido.apodo && <span className="block text-base font-normal text-crema/70">para {pedido.apodo}</span>}
        </h3>
        <span className={`text-lg ${ESTILO_URGENCIA[nivel]}`}>{minutos === 0 ? 'ahora' : `hace ${minutos} min`}</span>
      </div>

      <ul className="space-y-1.5 mb-3">
        {pedido.items.map((item, i) => (
          <li key={i} className="text-xl leading-snug">
            <span className="font-bold text-dorado mr-2">{item.cantidad}×</span>
            {item.nombreItem}
            {/* "Sin cebolla" (ingredientes quitables, 2026-11-26): en cocina es lo
                que mas importa ver, asi que va grande y en rojo. Hasta que se
                mergee feature/ingredientes-quitables el campo no existe y esto
                no muestra nada. */}
            {item.sinIngredientes?.length > 0 && (
              <span className="mt-1.5 block w-fit rounded-lg bg-sin-fondo px-2.5 py-1 text-base font-bold text-sin first-letter:uppercase">
                Sin {item.sinIngredientes.join(', ').toLowerCase()}
              </span>
            )}
          </li>
        ))}
      </ul>

      {pedido.notas && (
        <p className="text-lg text-dorado bg-dorado/10 border border-dorado/30 rounded-lg px-3 py-2 mb-3">
          “{pedido.notas}”
        </p>
      )}

      <div className="flex items-center justify-between gap-3">
        <span className="text-xs text-crema/30">#{pedido.id}</span>
        <button
          onClick={onAvanzar}
          disabled={actualizando}
          className={`text-lg font-semibold px-6 py-3 rounded-xl min-w-[9rem] transition-colors duration-150 disabled:opacity-50 ${estiloBoton}`}
        >
          {actualizando ? '…' : accion}
        </button>
      </div>
    </article>
  )
}
