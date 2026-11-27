import { useEffect, useState } from 'react'
import { obtenerPedidosDeMesa } from '../api/publico.js'

/**
 * "Pedido de la mesa" (2026-11-27): lo que pidió cada persona de la mesa
 * desde su celular, agrupado por apodo, con el estado de cada pedido. Se
 * actualiza solo mientras está abierto (el mesero confirma, la cocina avanza).
 */
const INTERVALO_MS = 8000

const formatoPrecio = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })

const ESTADO = {
  POR_CONFIRMAR: { texto: 'Esperando al mesero', clase: 'bg-amber-100 text-amber-800' },
  RECIBIDO: { texto: 'En cocina', clase: 'bg-sky-100 text-sky-800' },
  EN_PREPARACION: { texto: 'Preparando', clase: 'bg-sky-100 text-sky-800' },
  LISTO: { texto: 'Listo', clase: 'bg-green-100 text-green-800' },
  ENTREGADO: { texto: 'Entregado', clase: 'bg-carbon-950/5 text-carbon-900/60' },
  CANCELADO: { texto: 'Cancelado', clase: 'bg-red-50 text-red-700' },
}

const SIN_NOMBRE = 'Sin nombre'

// sinEnviar: platos que este comensal tiene en el carrito y aún no mandó.
// Sin este aviso, quien abre la vista antes de enviar ve "nadie ha pedido" y
// cree que su pedido se perdió (pasó probándolo en el celular, 2026-11-27).
export default function PedidoDeMesa({ token, miApodo, sinEnviar = 0, onVerMiPedido, onCerrar }) {
  const [pedidos, setPedidos] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let vivo = true
    const cargar = () =>
      obtenerPedidosDeMesa(token)
        .then((datos) => {
          if (vivo) {
            setPedidos(datos)
            setError('')
          }
        })
        .catch((e) => vivo && setError(e.message))
    cargar()
    const intervalo = setInterval(cargar, INTERVALO_MS)
    const onKey = (e) => e.key === 'Escape' && onCerrar()
    document.addEventListener('keydown', onKey)
    return () => {
      vivo = false
      clearInterval(intervalo)
      document.removeEventListener('keydown', onKey)
    }
  }, [token, onCerrar])

  // Agrupar por persona, en el orden en que pidieron.
  const porPersona = new Map()
  for (const p of pedidos || []) {
    const quien = p.apodo || SIN_NOMBRE
    if (!porPersona.has(quien)) porPersona.set(quien, [])
    porPersona.get(quien).push(p)
  }
  const vigentes = (pedidos || []).filter((p) => p.estado !== 'CANCELADO')
  const total = vigentes.reduce((acc, p) => acc + Number(p.total), 0)
  const hayPorConfirmar = vigentes.some((p) => p.estado === 'POR_CONFIRMAR')

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center" role="dialog" aria-modal="true" aria-label="Pedido de la mesa">
      <div className="absolute inset-0 bg-carbon-950/50" onClick={onCerrar} aria-hidden="true" />
      <div
        className="relative w-full sm:max-w-md bg-papel-100 rounded-t-3xl sm:rounded-2xl max-h-[90dvh] overflow-y-auto animate-fadeInUp"
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
      >
        <div className="sticky top-0 bg-papel-100 px-5 pt-5 pb-3 flex items-center justify-between border-b border-carbon-950/10">
          <h2 className="font-display text-xl text-carbon-950">Pedido de la mesa</h2>
          <button onClick={onCerrar} aria-label="Cerrar" className="w-9 h-9 rounded-full bg-carbon-950/5 text-carbon-950">
            ✕
          </button>
        </div>

        <div className="px-5 py-4">
          {error && <p className="text-sm text-red-700 mb-3">{error}</p>}
          {sinEnviar > 0 && (
            <div className="rounded-xl border border-carbon-950/10 bg-white p-3 mb-4 flex items-center justify-between gap-3">
              <p className="text-sm text-carbon-950">
                Tienes {sinEnviar} {sinEnviar === 1 ? 'plato' : 'platos'} en tu carrito <strong>sin enviar</strong>.
              </p>
              {onVerMiPedido && (
                <button
                  type="button"
                  onClick={onVerMiPedido}
                  className="shrink-0 rounded-full px-3 py-1.5 text-xs font-semibold text-white"
                  style={{ backgroundColor: 'var(--color-acento)' }}
                >
                  Enviar mi pedido
                </button>
              )}
            </div>
          )}
          {!pedidos ? (
            <div className="h-24 rounded-xl bg-carbon-950/5 animate-pulse" />
          ) : pedidos.length === 0 ? (
            <p className="text-sm text-carbon-900/50 py-8 text-center">
              {sinEnviar > 0 ? 'Todavía no hay pedidos enviados en esta mesa.' : 'Todavía nadie ha pedido en esta mesa.'}
            </p>
          ) : (
            <>
              {hayPorConfirmar && (
                <p className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-4">
                  El mesero pasará a confirmar los pedidos pendientes antes de mandarlos a la cocina.
                </p>
              )}
              <div className="space-y-5">
                {[...porPersona.entries()].map(([quien, suyos]) => (
                  <section key={quien}>
                    <h3 className="text-sm font-semibold text-carbon-950 mb-2">
                      {quien}
                      {miApodo && quien === miApodo && <span className="text-carbon-900/40 font-normal"> (tú)</span>}
                    </h3>
                    <ul className="space-y-2">
                      {suyos.map((p) => (
                        <li key={p.id} className={`rounded-xl bg-white border border-carbon-950/5 p-3 ${p.estado === 'CANCELADO' ? 'opacity-50' : ''}`}>
                          <div className="flex items-center justify-between mb-1.5">
                            <span className="text-[11px] text-carbon-900/40">Pedido #{p.id}</span>
                            <span className={`text-[11px] font-semibold rounded-full px-2 py-0.5 ${ESTADO[p.estado]?.clase || ''}`}>
                              {ESTADO[p.estado]?.texto || p.estado}
                            </span>
                          </div>
                          {p.items.map((it, i) => (
                            <div key={i} className="flex justify-between gap-2 text-sm">
                              <span className={`text-carbon-900/80 ${p.estado === 'CANCELADO' ? 'line-through' : ''}`}>
                                {it.cantidad}× {it.nombreItem}
                                {it.sinIngredientes?.length > 0 && (
                                  <span className="block text-xs font-semibold text-red-700">
                                    Sin {it.sinIngredientes.join(', ').toLowerCase()}
                                  </span>
                                )}
                              </span>
                              <span className="text-carbon-900/60 whitespace-nowrap">{formatoPrecio.format(it.subtotal)}</span>
                            </div>
                          ))}
                        </li>
                      ))}
                    </ul>
                  </section>
                ))}
              </div>
              <div className="flex justify-between text-base font-semibold border-t border-carbon-950/10 mt-5 pt-3">
                <span className="text-carbon-950">Total de la mesa</span>
                <span style={{ color: 'var(--color-acento)' }}>{formatoPrecio.format(total)}</span>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
