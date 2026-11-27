import { useCallback, useEffect, useRef, useState } from 'react'
import { useApiJson, useAuth } from '../context/AuthContext.jsx'
import LlamadosPendientes from '../components/LlamadosPendientes.jsx'
import MesasPorConfirmar from '../components/MesasPorConfirmar.jsx'

const formatoPrecio = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
})

const formatoHora = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit' })

// Espejo EXACTO de PedidoService.TRANSICIONES_VALIDAS en el backend -- esto
// es solo para decidir que botones mostrar en la UI. La fuente de verdad
// real de que transiciones son validas sigue siendo el backend (ver
// PedidoExceptions.TransicionEstadoInvalidaException): si este mapa alguna
// vez queda desactualizado respecto al backend, el peor caso es un boton
// que aparece pero el servidor rechaza con un 409 claro, nunca al reves.
const TRANSICIONES = {
  POR_CONFIRMAR: ['RECIBIDO', 'CANCELADO'],
  RECIBIDO: ['EN_PREPARACION', 'CANCELADO'],
  EN_PREPARACION: ['LISTO', 'CANCELADO'],
  LISTO: ['ENTREGADO', 'CANCELADO'],
  ENTREGADO: [],
  CANCELADO: [],
}

const ESTILO_ESTADO = {
  POR_CONFIRMAR: 'bg-dorado/15 text-dorado border-dorado/30',
  RECIBIDO: 'bg-dorado/15 text-dorado border-dorado/30',
  EN_PREPARACION: 'bg-blue-500/15 text-blue-300 border-blue-500/30',
  LISTO: 'bg-green-500/15 text-green-300 border-green-500/30',
  ENTREGADO: 'bg-carbon-700 text-crema/40 border-carbon-600',
  CANCELADO: 'bg-red-950/40 text-red-300 border-red-900/60',
}

const ETIQUETA_ESTADO = {
  POR_CONFIRMAR: 'Por confirmar',
  RECIBIDO: 'Recibido',
  EN_PREPARACION: 'En preparación',
  LISTO: 'Listo',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
}

const ETIQUETA_ACCION = {
  EN_PREPARACION: 'Marcar en preparación',
  LISTO: 'Marcar listo',
  ENTREGADO: 'Marcar entregado',
  CANCELADO: 'Cancelar pedido',
}

// RF-09/panel: sin WebSocket todavia (ver documentacion-tecnica.md,
// roadmap) -- este intervalo de polling es la forma en que el mesero se
// entera de un pedido nuevo. 6s es un punto medio deliberado entre "se
// siente en vivo" y "no satura el backend de un solo VPS con requests
// constantes" -- no es un numero magico verificado con carga real, es un
// valor de partida razonable.
const INTERVALO_POLLING_MS = 6000

// Antes cada polling traia TODO el historial de pedidos del tenant (con sus
// items y comensal, N+1 en el backend): con meses de operacion eso crece
// sin limite cada 6s. Ahora cada vista pide al backend solo los estados que
// muestra (?estado=..., el mismo filtro que ya usa la vista de cocina), y
// solo "Activos" hace polling: el historial no cambia solo (un pedido pasa
// a ENTREGADO/CANCELADO por una accion en esta misma pantalla), asi que se
// pide una vez al abrir esa pestaña.
const ESTADOS_POR_VISTA = {
  activos: ['RECIBIDO', 'EN_PREPARACION', 'LISTO'],
  historial: ['ENTREGADO', 'CANCELADO'],
}

export default function PedidosPage() {
  const apiJson = useApiJson()
  const { usuario } = useAuth()
  // Habeas Data (documentacion-tecnica.md §10): borrar datos personales de
  // un comensal es al menos tan sensible como leerlos -- mismo criterio de
  // rol que ComensalAdminController en el backend (no MESERO).
  const puedeEliminarDatosComensal = usuario?.rol === 'ADMIN_RESTAURANTE'

  const [vista, setVista] = useState('activos') // 'activos' | 'historial'
  const [pedidos, setPedidos] = useState([])
  const [mesasPorId, setMesasPorId] = useState({})
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [actualizandoId, setActualizandoId] = useState(null)
  const [eliminandoComensalId, setEliminandoComensalId] = useState(null)

  // Evita que dos llamadas a cargarPedidos() (una del polling, otra
  // disparada por una accion del usuario) se pisen mostrando datos viejos
  // si la mas vieja responde despues que la mas nueva.
  const peticionIdRef = useRef(0)

  const cargarPedidos = useCallback(
    async (vistaACargar, mostrarSpinner) => {
      const idPeticion = ++peticionIdRef.current
      if (mostrarSpinner) setCargando(true)
      try {
        const porEstado = await Promise.all(
          ESTADOS_POR_VISTA[vistaACargar].map((estado) => apiJson(`/api/admin/pedidos?estado=${estado}`)),
        )
        if (idPeticion !== peticionIdRef.current) return
        setPedidos(porEstado.flat())
        setError('')
      } catch (e) {
        if (idPeticion !== peticionIdRef.current) return
        setError(e.message)
      } finally {
        if (idPeticion === peticionIdRef.current && mostrarSpinner) setCargando(false)
      }
    },
    [apiJson],
  )

  useEffect(() => {
    apiJson('/api/admin/mesas')
      .then((mesas) => {
        setMesasPorId(Object.fromEntries(mesas.map((m) => [m.id, m.codigo])))
      })
      .catch(() => {
        // Si esto falla, las tarjetas de pedido igual funcionan mostrando
        // "Mesa #<id>" (ver abajo) -- no vale la pena bloquear toda la
        // pantalla de pedidos por un fallo al traer los nombres de mesa.
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    // Al cambiar de pestaña se vacia la lista para no mostrar un instante
    // los pedidos de la otra vista mientras llega la respuesta.
    setPedidos([])
    cargarPedidos(vista, true)
    if (vista !== 'activos') return undefined
    const intervalo = setInterval(() => cargarPedidos('activos', false), INTERVALO_POLLING_MS)
    return () => clearInterval(intervalo)
  }, [cargarPedidos, vista])

  async function onCambiarEstado(pedido, nuevoEstado) {
    if (nuevoEstado === 'CANCELADO' && !window.confirm(`¿Cancelar el pedido de ${nombreMesa(pedido.mesaId)}?`)) {
      return
    }
    setActualizandoId(pedido.id)
    setError('')
    try {
      const actualizado = await apiJson(`/api/admin/pedidos/${pedido.id}/estado`, {
        method: 'PATCH',
        body: { estado: nuevoEstado },
      })
      setPedidos((prev) => prev.map((p) => (p.id === actualizado.id ? actualizado : p)))
    } catch (e) {
      setError(e.message)
    } finally {
      setActualizandoId(null)
    }
  }

  async function onEliminarDatosComensal(pedido) {
    if (
      !window.confirm(
        'Esto elimina el nombre y teléfono de este comensal de forma permanente (Habeas Data). El pedido se conserva, solo deja de estar asociado a esa persona. No se puede deshacer.',
      )
    ) {
      return
    }
    setEliminandoComensalId(pedido.comensal.id)
    setError('')
    try {
      await apiJson(`/api/admin/comensales/${pedido.comensal.id}`, { method: 'DELETE' })
      setPedidos((prev) => prev.map((p) => (p.id === pedido.id ? { ...p, comensal: null } : p)))
    } catch (e) {
      setError(e.message)
    } finally {
      setEliminandoComensalId(null)
    }
  }

  function nombreMesa(mesaId) {
    return mesasPorId[mesaId] ? mesasPorId[mesaId] : `Mesa #${mesaId}`
  }

  const pedidosVisibles = pedidos
    // El backend ya filtra por estado; este filtro sigue haciendo falta para
    // que un pedido que se acaba de marcar ENTREGADO/CANCELADO desaparezca
    // de "Activos" al instante, sin esperar al siguiente polling.
    .filter((p) => ESTADOS_POR_VISTA[vista].includes(p.estado))
    // Los mas nuevos primero en "activos" (hay que atenderlos ya), los mas
    // recientes primero tambien en "historial" (lo ultimo que paso es lo
    // mas relevante para revisar).
    .sort((a, b) => new Date(b.creadoEn) - new Date(a.creadoEn))

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="font-display font-semibold text-2xl text-crema">Pedidos</h1>
          <p className="text-sm text-crema/40">
            {vista === 'activos'
              ? `Se actualiza solo cada ${Math.round(INTERVALO_POLLING_MS / 1000)}s. Los pedidos nuevos aparecen arriba.`
              : 'Pedidos entregados y cancelados.'}
          </p>
        </div>
      </div>

      <LlamadosPendientes nombreMesa={nombreMesa} />
      {vista === 'activos' && <MesasPorConfirmar nombreMesa={nombreMesa} onConfirmada={() => cargarPedidos('activos', false)} />}

      <div className="flex gap-2 mb-5">
        <button
          onClick={() => setVista('activos')}
          className={`text-xs font-medium px-3.5 py-1.5 rounded-full transition-colors duration-150 ${
            vista === 'activos' ? 'bg-dorado text-carbon-950' : 'bg-carbon-800 text-crema/60'
          }`}
        >
          Activos
        </button>
        <button
          onClick={() => setVista('historial')}
          className={`text-xs font-medium px-3.5 py-1.5 rounded-full transition-colors duration-150 ${
            vista === 'historial' ? 'bg-dorado text-carbon-950' : 'bg-carbon-800 text-crema/60'
          }`}
        >
          Historial
        </button>
      </div>

      {error && (
        <div className="mb-4 text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2 animate-fadeInUp">
          {error}
        </div>
      )}

      {cargando ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
          {[1, 2, 3].map((n) => (
            <div key={n} className="h-56 rounded-2xl bg-carbon-800 animate-pulseSoft" />
          ))}
        </div>
      ) : pedidosVisibles.length === 0 ? (
        <div className="text-sm text-crema/40 py-16 text-center">
          {vista === 'activos' ? 'No hay pedidos activos en este momento.' : 'Todavía no hay pedidos en el historial.'}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
          {pedidosVisibles.map((pedido) => (
            <TarjetaPedido
              key={pedido.id}
              pedido={pedido}
              nombreMesa={nombreMesa(pedido.mesaId)}
              actualizando={actualizandoId === pedido.id}
              onCambiarEstado={(nuevoEstado) => onCambiarEstado(pedido, nuevoEstado)}
              puedeEliminarDatosComensal={puedeEliminarDatosComensal}
              eliminandoDatosComensal={eliminandoComensalId === pedido.comensal?.id}
              onEliminarDatosComensal={() => onEliminarDatosComensal(pedido)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function TarjetaPedido({
  pedido,
  nombreMesa,
  actualizando,
  onCambiarEstado,
  puedeEliminarDatosComensal,
  eliminandoDatosComensal,
  onEliminarDatosComensal,
}) {
  const transicionesDisponibles = TRANSICIONES[pedido.estado] || []

  return (
    <div className="card p-4 animate-fadeInUp flex flex-col">
      <div className="flex items-start justify-between gap-2 mb-2">
        <div>
          <h3 className="font-medium text-crema">
            {nombreMesa}
            {pedido.apodo && <span className="text-crema/60 font-normal"> · para {pedido.apodo}</span>}
          </h3>
          <p className="text-[11px] text-crema/40">
            #{pedido.id} · {formatoHora.format(new Date(pedido.creadoEn))}
          </p>
        </div>
        <span className={`text-[11px] font-medium px-2.5 py-1 rounded-full border ${ESTILO_ESTADO[pedido.estado] || ''}`}>
          {ETIQUETA_ESTADO[pedido.estado] || pedido.estado}
        </span>
      </div>

      <div className="space-y-1 my-3 flex-1">
        {pedido.items.map((item, indice) => (
          <div key={indice} className="flex items-start justify-between gap-2 text-sm">
            <span className="text-crema/80">
              {item.cantidad}× {item.nombreItem}
              {/* "Sin cebolla" (2026-11-26): lo que el comensal quito, bien visible. */}
              {item.sinIngredientes?.length > 0 && (
                <span className="mt-1 block w-fit pastilla-sin">
                  Sin {item.sinIngredientes.join(', ').toLowerCase()}
                </span>
              )}
            </span>
            <span className="text-crema/50 whitespace-nowrap">{formatoPrecio.format(item.subtotal)}</span>
          </div>
        ))}
      </div>

      {pedido.notas && (
        <p className="text-xs text-dorado/80 bg-dorado/10 border border-dorado/20 rounded-lg px-2.5 py-1.5 mb-3">
          “{pedido.notas}”
        </p>
      )}

      {pedido.comensal && (pedido.comensal.nombre || pedido.comensal.telefono) && (
        <div className="flex items-center justify-between gap-2 mb-3">
          <p className="text-[11px] text-crema/40">
            {[pedido.comensal.nombre, pedido.comensal.telefono].filter(Boolean).join(' · ')}
          </p>
          {puedeEliminarDatosComensal && (
            <button
              onClick={onEliminarDatosComensal}
              disabled={eliminandoDatosComensal}
              className="text-[10px] text-crema/30 hover:text-red-300 transition-colors duration-150 whitespace-nowrap"
            >
              {eliminandoDatosComensal ? '…' : 'Eliminar datos'}
            </button>
          )}
        </div>
      )}

      <div className="flex items-center justify-between text-sm font-semibold border-t border-carbon-700 pt-3 mb-3">
        <span className="text-crema/60 font-normal">Total</span>
        <span className="text-crema">{formatoPrecio.format(pedido.total)}</span>
      </div>

      {transicionesDisponibles.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {transicionesDisponibles.map((estado) => (
            <button
              key={estado}
              onClick={() => onCambiarEstado(estado)}
              disabled={actualizando}
              className={estado === 'CANCELADO' ? 'btn-peligro flex-1' : 'btn-secundario flex-1 text-xs py-2'}
            >
              {actualizando ? '…' : ETIQUETA_ACCION[estado]}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
