import { useEffect, useState } from 'react'
import { useApiJson } from '../context/AuthContext.jsx'

/**
 * "Que quitan tus clientes" (2026-11-27): lo que el comensal saca de cada
 * plato desde la vista de ingredientes. Es el dato que ningun competidor
 * tiene y le sirve al dueno para ajustar recetas y compras.
 */
const PERIODOS = [
  [7, 'Últimos 7 días'],
  [30, 'Últimos 30 días'],
  [90, 'Últimos 90 días'],
]

const formatoPct = (n) => `${n.toLocaleString('es-CO', { maximumFractionDigits: 1 })}%`

export default function ReportesPage() {
  const apiJson = useApiJson()
  const [dias, setDias] = useState(30)
  const [reporte, setReporte] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelado = false
    setCargando(true)
    apiJson(`/api/admin/reportes/ingredientes-quitados?dias=${dias}`)
      .then((datos) => {
        if (!cancelado) {
          setReporte(datos)
          setError('')
        }
      })
      .catch((e) => !cancelado && setError(e.message))
      .finally(() => !cancelado && setCargando(false))
    return () => {
      cancelado = true
    }
  }, [apiJson, dias])

  const destacado = reporte?.masQuitados?.[0]

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-3 mb-6">
        <div>
          <h1 className="font-display text-2xl text-crema">Qué quitan tus clientes</h1>
          <p className="text-sm text-crema/40">Lo que tus clientes sacan de cada plato al pedir desde la carta.</p>
        </div>
        <div className="flex gap-2" role="group" aria-label="Período">
          {PERIODOS.map(([valor, etiqueta]) => (
            <button
              key={valor}
              onClick={() => setDias(valor)}
              aria-pressed={dias === valor}
              className={`text-xs font-medium px-3.5 py-1.5 rounded-full transition-colors duration-150 ${
                dias === valor ? 'bg-dorado text-carbon-950' : 'bg-carbon-800 text-crema/60'
              }`}
            >
              {etiqueta}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div className="mb-4 text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2">{error}</div>
      )}

      {cargando && !reporte ? (
        <div className="h-40 rounded-2xl bg-carbon-800 animate-pulseSoft" />
      ) : reporte && reporte.unidadesPersonalizadas === 0 ? (
        <div className="card p-8 text-center">
          <p className="text-crema/70 mb-1">Todavía no hay platos personalizados en este período.</p>
          <p className="text-sm text-crema/40">
            Cuando tus clientes quiten ingredientes desde la carta, aquí verás qué sacan y con qué frecuencia.
            {reporte.unidadesVendidas > 0 && ` Llevas ${reporte.unidadesVendidas} platos vendidos.`}
          </p>
        </div>
      ) : reporte ? (
        <div className={`space-y-6 ${cargando ? 'opacity-60' : ''}`}>
          {/* El dato principal en una frase: lo que el dueno se lleva. */}
          {destacado && (
            <div className="card p-5 border-dorado/30">
              <p className="text-lg text-crema leading-snug">
                El <strong className="text-dorado">{formatoPct(destacado.porcentaje)}</strong> de las veces que se
                pide <strong>{destacado.plato}</strong>, lo piden <strong>sin {destacado.nombre.toLowerCase()}</strong>.
              </p>
              <p className="text-sm text-crema/50 mt-1">
                En total, {formatoPct(reporte.porcentajePersonalizado)} de los {reporte.unidadesVendidas} platos vendidos
                se pidieron personalizados.
              </p>
            </div>
          )}

          {reporte.masQuitados.length > 1 && (
            <section>
              <h2 className="text-sm font-semibold text-crema/70 mb-3">Lo más quitado</h2>
              <ol className="card divide-y divide-carbon-700">
                {reporte.masQuitados.map((q, i) => (
                  <li key={`${q.plato}-${q.nombre}`} className="flex items-center gap-3 px-4 py-3">
                    <span className="text-crema/30 w-5 text-sm tabular-nums">{i + 1}</span>
                    <span className="flex-1 text-sm text-crema">
                      Sin {q.nombre.toLowerCase()} <span className="text-crema/40">· {q.plato}</span>
                    </span>
                    <span className="text-sm font-semibold text-dorado tabular-nums">{formatoPct(q.porcentaje)}</span>
                  </li>
                ))}
              </ol>
            </section>
          )}

          <section>
            <h2 className="text-sm font-semibold text-crema/70 mb-3">Por plato</h2>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {reporte.platos.map((plato) => (
                <article key={plato.itemMenuId} className="card p-4">
                  <div className="flex items-baseline justify-between gap-2 mb-1">
                    <h3 className="font-medium text-crema">{plato.nombre}</h3>
                    <span className="text-xs text-crema/40 whitespace-nowrap">{plato.unidadesVendidas} vendidos</span>
                  </div>
                  <p className="text-xs text-crema/50 mb-3">
                    {formatoPct(plato.porcentajePersonalizado)} se pidió personalizado
                  </p>
                  <ul className="space-y-2">
                    {plato.ingredientes.map((ing) => (
                      <li key={ing.nombre}>
                        <div className="flex justify-between text-sm mb-1">
                          <span className="text-crema/80">Sin {ing.nombre.toLowerCase()}</span>
                          <span className="text-crema/60 tabular-nums">
                            {formatoPct(ing.porcentaje)} <span className="text-crema/30">({ing.unidades})</span>
                          </span>
                        </div>
                        <div className="h-1.5 rounded-full bg-carbon-700 overflow-hidden" aria-hidden="true">
                          <div className="h-full rounded-full bg-dorado" style={{ width: `${Math.min(100, ing.porcentaje)}%` }} />
                        </div>
                      </li>
                    ))}
                  </ul>
                </article>
              ))}
            </div>
          </section>
        </div>
      ) : null}
    </div>
  )
}
