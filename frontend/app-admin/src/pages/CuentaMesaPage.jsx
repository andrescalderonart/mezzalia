import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useApiJson } from '../context/AuthContext.jsx'

const formatoPrecio = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })
const formatoFechaHora = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' })
const SIN_NOMBRE = 'Sin nombre'
// Ley 1935 de 2018: la propina es voluntaria y lo sugerido no puede pasar del 10 %.
const PROPINA_SUGERIDA = 0.1

/**
 * "La cuenta de la mesa" (2026-11-27, pilotos): resumen imprimible de lo que
 * pidió la mesa desde su QR vigente, agrupado por persona, para que el cajero
 * lo registre en su POS. NO es factura: Mezzalia no factura ante la DIAN (ver
 * docs/calidad y el estudio de mercado), así que el papel lo dice en claro.
 * La propina se muestra como sugerida y voluntaria, para que el mesero
 * pregunte antes de facturar, como exige la ley.
 */
export default function CuentaMesaPage() {
  const { mesaId } = useParams()
  const apiJson = useApiJson()
  const [pedidos, setPedidos] = useState(null)
  const [nombreMesa, setNombreMesa] = useState(`Mesa #${mesaId}`)
  const [error, setError] = useState('')

  useEffect(() => {
    let vivo = true
    apiJson(`/api/admin/pedidos/mesa/${mesaId}/cuenta`)
      .then((datos) => vivo && setPedidos(datos))
      .catch((e) => vivo && setError(e.message))
    apiJson('/api/admin/mesas')
      .then((mesas) => {
        const mesa = mesas.find((m) => String(m.id) === String(mesaId))
        if (vivo && mesa) setNombreMesa(mesa.codigo)
      })
      .catch(() => {})
    return () => {
      vivo = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mesaId])

  const vigentes = (pedidos || []).filter((p) => p.estado !== 'CANCELADO')
  const porConfirmar = vigentes.filter((p) => p.estado === 'POR_CONFIRMAR').length
  const porPersona = new Map()
  for (const p of vigentes) {
    const quien = p.apodo || SIN_NOMBRE
    if (!porPersona.has(quien)) porPersona.set(quien, [])
    porPersona.get(quien).push(p)
  }
  const total = vigentes.reduce((acc, p) => acc + Number(p.total), 0)
  const propina = Math.round(total * PROPINA_SUGERIDA)

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6 print:hidden">
        <div>
          <Link to="/pedidos" className="text-sm text-crema/55 hover:text-crema">
            ← Pedidos
          </Link>
          <h1 className="text-2xl font-semibold text-crema mt-1">Cuenta de {nombreMesa}</h1>
        </div>
        <button onClick={() => window.print()} disabled={!pedidos || vigentes.length === 0} className="btn-primario">
          Imprimir
        </button>
      </div>

      {error && <p className="text-sm text-peligro mb-4">{error}</p>}
      {porConfirmar > 0 && (
        <p className="text-sm text-crema bg-dorado/10 rounded-lg px-3 py-2 mb-4 print:hidden">
          Hay {porConfirmar} {porConfirmar === 1 ? 'pedido' : 'pedidos'} sin confirmar en esta mesa. Confírmalos en Pedidos
          antes de cobrar.
        </p>
      )}

      {!pedidos ? (
        <div className="h-64 rounded-xl bg-carbon-800 animate-pulseSoft" />
      ) : vigentes.length === 0 ? (
        <p className="text-sm text-crema/55 py-16 text-center">Esta mesa no tiene pedidos desde que se abrió su código QR.</p>
      ) : (
        // El "papel": ancho de tirilla en pantalla; en la impresora ocupa la página.
        <article className="card max-w-md mx-auto p-6 print:max-w-none print:border-0 print:shadow-none print:p-0 text-crema">
          <header className="text-center mb-4">
            <h2 className="text-xl font-semibold">{nombreMesa}</h2>
            <p className="text-xs text-crema/55">{formatoFechaHora.format(new Date())}</p>
          </header>

          {[...porPersona.entries()].map(([quien, suyos]) => (
            <section key={quien} className="border-t border-dashed border-carbon-600 py-3">
              <h3 className="text-sm font-semibold mb-1.5">{quien}</h3>
              {suyos.flatMap((p) =>
                p.items.map((it, i) => (
                  <div key={`${p.id}-${i}`} className="flex justify-between gap-3 text-sm py-0.5">
                    <span>
                      <span className="tabular-nums">{it.cantidad}×</span> {it.nombreItem}
                      {it.sinIngredientes?.length > 0 && (
                        <span className="block text-xs font-semibold text-peligro">
                          Sin {it.sinIngredientes.join(', ').toLowerCase()}
                        </span>
                      )}
                    </span>
                    <span className="tabular-nums whitespace-nowrap">{formatoPrecio.format(it.subtotal)}</span>
                  </div>
                )),
              )}
            </section>
          ))}

          <div className="border-t border-carbon-600 pt-3 space-y-1">
            <div className="flex justify-between text-base font-semibold">
              <span>Total consumido</span>
              <span className="tabular-nums">{formatoPrecio.format(total)}</span>
            </div>
            <div className="flex justify-between text-sm text-crema/70">
              <span>Propina sugerida (voluntaria, 10 %)</span>
              <span className="tabular-nums">{formatoPrecio.format(propina)}</span>
            </div>
          </div>

          <p className="mt-4 text-[11px] leading-snug text-crema/55">
            La propina es voluntaria: pregunte al cliente si desea incluirla antes de facturar (Ley 1935 de 2018). Este
            resumen no es una factura; la factura electrónica la emite el sistema POS del establecimiento.
          </p>
        </article>
      )}
    </div>
  )
}
