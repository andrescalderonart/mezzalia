import { useCallback, useEffect, useRef, useState } from 'react'
import { useApiJson } from '../context/AuthContext.jsx'

/**
 * "Pedido de la mesa" (2026-11-27): lo que las mesas pidieron desde el QR y
 * todavía no entra a cocina. El mesero pasa por la mesa, verifica ("¿ya
 * pidieron todos?") y confirma la mesa completa de una vez; o descarta un
 * pedido que no corresponde (foto del QR, pedido repetido…).
 *
 * Mismo polling que Pedidos. Si falla la carga no tapa la pantalla.
 */
const INTERVALO_POLLING_MS = 6000

const formatoPrecio = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })
const formatoHora = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit' })

export default function MesasPorConfirmar({ nombreMesa, onConfirmada }) {
  const apiJson = useApiJson()
  const [pedidos, setPedidos] = useState([])
  const [ocupado, setOcupado] = useState(null) // mesaId o `p${pedidoId}`
  const [error, setError] = useState('')
  const peticionRef = useRef(0)

  const cargar = useCallback(async () => {
    const id = ++peticionRef.current
    try {
      const datos = await apiJson('/api/admin/pedidos?estado=POR_CONFIRMAR')
      if (id === peticionRef.current) setPedidos(datos)
    } catch {
      // silencioso: se reintenta en el siguiente ciclo
    }
  }, [apiJson])

  useEffect(() => {
    cargar()
    const intervalo = setInterval(cargar, INTERVALO_POLLING_MS)
    return () => clearInterval(intervalo)
  }, [cargar])

  async function confirmarMesa(mesaId) {
    setOcupado(mesaId)
    setError('')
    try {
      await apiJson(`/api/admin/pedidos/mesa/${mesaId}/confirmar`, { method: 'PATCH' })
      setPedidos((prev) => prev.filter((p) => p.mesaId !== mesaId))
      onConfirmada?.()
    } catch (e) {
      setError(e.message)
      await cargar()
    } finally {
      setOcupado(null)
    }
  }

  async function descartar(pedido) {
    if (!window.confirm(`¿Descartar el pedido #${pedido.id}${pedido.apodo ? ` de ${pedido.apodo}` : ''}? No llegará a la cocina.`)) return
    setOcupado(`p${pedido.id}`)
    setError('')
    try {
      await apiJson(`/api/admin/pedidos/${pedido.id}/estado`, { method: 'PATCH', body: { estado: 'CANCELADO' } })
      setPedidos((prev) => prev.filter((p) => p.id !== pedido.id))
    } catch (e) {
      setError(e.message)
    } finally {
      setOcupado(null)
    }
  }

  if (pedidos.length === 0) return null

  // Agrupar por mesa, en el orden del primer pedido.
  const porMesa = new Map()
  for (const p of pedidos) {
    if (!porMesa.has(p.mesaId)) porMesa.set(p.mesaId, [])
    porMesa.get(p.mesaId).push(p)
  }

  return (
    <section aria-label="Mesas por confirmar" className="mb-6">
      <h2 className="text-sm font-semibold text-dorado mb-2">Por confirmar</h2>
      {error && <p className="text-sm text-red-300 mb-2">{error}</p>}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        {[...porMesa.entries()].map(([mesaId, suyos]) => {
          const total = suyos.reduce((acc, p) => acc + Number(p.total), 0)
          return (
            <article key={mesaId} className="rounded-2xl border-2 border-dorado/50 bg-dorado/5 p-4 animate-fadeInUp">
              <div className="flex items-baseline justify-between gap-2 mb-3">
                <h3 className="text-lg font-semibold text-crema">{nombreMesa(mesaId)}</h3>
                <span className="text-xs text-crema/50">
                  {suyos.length} {suyos.length === 1 ? 'pedido' : 'pedidos'} · desde {formatoHora.format(new Date(suyos[0].creadoEn))}
                </span>
              </div>
              <ul className="space-y-2.5 mb-4">
                {suyos.map((p) => (
                  <li key={p.id} className="text-sm">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-crema">{p.apodo || 'Sin nombre'}</span>
                      <button
                        onClick={() => descartar(p)}
                        disabled={ocupado === `p${p.id}`}
                        className="text-[11px] text-crema/40 hover:text-red-300"
                      >
                        Descartar
                      </button>
                    </div>
                    {p.items.map((it, i) => (
                      <div key={i} className="pl-3 text-crema/80">
                        {it.cantidad}× {it.nombreItem}
                        {it.sinIngredientes?.length > 0 && (
                          <span className="ml-2 pastilla-sin">
                            sin {it.sinIngredientes.join(', ').toLowerCase()}
                          </span>
                        )}
                      </div>
                    ))}
                    {p.notas && <p className="pl-3 text-xs text-dorado/80">“{p.notas}”</p>}
                  </li>
                ))}
              </ul>
              <button onClick={() => confirmarMesa(mesaId)} disabled={ocupado === mesaId} className="btn-primario w-full">
                {ocupado === mesaId ? 'Confirmando…' : `Confirmar mesa y enviar a cocina · ${formatoPrecio.format(total)}`}
              </button>
            </article>
          )
        })}
      </div>
    </section>
  )
}
