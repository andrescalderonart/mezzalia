import { useCallback, useEffect, useRef, useState } from 'react'
import { useApiJson } from '../context/AuthContext.jsx'
import { Link } from 'react-router-dom'
import { IconoCampana, IconoCuenta } from './iconos.jsx'

/**
 * Mesas que llamaron al mesero o pidieron la cuenta desde el QR (enfoque en
 * bares, 2026-11-26). Va arriba de Pedidos, que es la pantalla del mesero
 * durante el turno. Mismo polling que PedidosPage (sin WebSocket todavia).
 *
 * Si falla la carga no se muestra error: los pedidos siguen funcionando y no
 * vale la pena tapar la pantalla por esto; en el siguiente ciclo se reintenta.
 */
const INTERVALO_POLLING_MS = 6000

const TEXTO = {
  MESERO: { Icono: IconoCampana, accion: 'llama al mesero' },
  CUENTA: { Icono: IconoCuenta, accion: 'pide la cuenta' },
}

const formatoHora = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit' })

export default function LlamadosPendientes({ nombreMesa }) {
  const apiJson = useApiJson()
  const [llamados, setLlamados] = useState([])
  const [atendiendoId, setAtendiendoId] = useState(null)
  const peticionIdRef = useRef(0)

  const cargar = useCallback(async () => {
    const id = ++peticionIdRef.current
    try {
      const datos = await apiJson('/api/admin/llamados')
      if (id === peticionIdRef.current) setLlamados(datos)
    } catch {
      // Ver el comentario de arriba: silencioso a proposito.
    }
  }, [apiJson])

  useEffect(() => {
    cargar()
    const intervalo = setInterval(cargar, INTERVALO_POLLING_MS)
    return () => clearInterval(intervalo)
  }, [cargar])

  async function atender(llamado) {
    setAtendiendoId(llamado.id)
    try {
      await apiJson(`/api/admin/llamados/${llamado.id}/atender`, { method: 'PATCH' })
      setLlamados((prev) => prev.filter((l) => l.id !== llamado.id))
    } catch {
      await cargar()
    } finally {
      setAtendiendoId(null)
    }
  }

  if (llamados.length === 0) return null

  return (
    <section aria-label="Mesas que llaman" className="mb-6 space-y-2">
      {llamados.map((llamado) => {
        const { Icono, accion } = TEXTO[llamado.tipo] || TEXTO.MESERO
        return (
        <div key={llamado.id} className="card flex items-center gap-3 px-3 py-2.5 animate-fadeInUp">
          <span className="relative w-9 h-9 shrink-0 rounded-full bg-dorado/10 text-dorado flex items-center justify-center">
            <Icono className="w-[18px] h-[18px]" />
            <span className="absolute top-0 right-0 w-2.5 h-2.5 rounded-full bg-dorado ring-2 ring-carbon-900" aria-hidden="true" />
          </span>
          <p className="flex-1 min-w-0 text-sm text-crema">
            <strong className="font-semibold">{nombreMesa(llamado.mesaId)}</strong> {accion}
            <span className="block text-xs text-crema/50">{formatoHora.format(new Date(llamado.creadoEn))}</span>
          </p>
          {llamado.tipo === 'CUENTA' && (
            <Link to={`/mesas/${llamado.mesaId}/cuenta`} className="btn-primario h-8 px-3 text-xs whitespace-nowrap">
              Ver cuenta
            </Link>
          )}
          <button
            type="button"
            onClick={() => atender(llamado)}
            disabled={atendiendoId === llamado.id}
            className="btn-secundario text-xs py-1.5 px-3 whitespace-nowrap"
          >
            {atendiendoId === llamado.id ? '…' : 'Atendido'}
          </button>
        </div>
        )
      })}
    </section>
  )
}
