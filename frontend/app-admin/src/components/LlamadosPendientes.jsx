import { useCallback, useEffect, useRef, useState } from 'react'
import { useApiJson } from '../context/AuthContext.jsx'

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
  MESERO: { icono: '🙋', accion: 'llama al mesero' },
  CUENTA: { icono: '🧾', accion: 'pide la cuenta' },
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
      {llamados.map((llamado) => (
        <div
          key={llamado.id}
          className={`flex items-center justify-between gap-3 rounded-xl border px-4 py-3 animate-fadeInUp ${
            llamado.tipo === 'CUENTA' ? 'border-dorado/40 bg-dorado/10' : 'border-terracota/50 bg-terracota/15'
          }`}
        >
          <p className="text-sm text-crema">
            <span className="mr-2" aria-hidden="true">
              {TEXTO[llamado.tipo]?.icono}
            </span>
            <strong className="font-semibold">{nombreMesa(llamado.mesaId)}</strong> {TEXTO[llamado.tipo]?.accion}
            <span className="text-crema/40 ml-2 text-xs">{formatoHora.format(new Date(llamado.creadoEn))}</span>
          </p>
          <button
            type="button"
            onClick={() => atender(llamado)}
            disabled={atendiendoId === llamado.id}
            className="btn-secundario text-xs py-1.5 px-3 whitespace-nowrap"
          >
            {atendiendoId === llamado.id ? '…' : 'Atendido'}
          </button>
        </div>
      ))}
    </section>
  )
}
