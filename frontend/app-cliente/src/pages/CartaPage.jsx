import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { obtenerCarta } from '../api/publico.js'

const formatoPrecio = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
})

export default function CartaPage() {
  const { token } = useParams()
  const [estado, setEstado] = useState('cargando') // 'cargando' | 'error' | 'listo'
  const [error, setError] = useState('')
  const [carta, setCarta] = useState(null)
  const [categoriaActiva, setCategoriaActiva] = useState(null)

  const seccionesRef = useRef({})
  const navRef = useRef(null)

  useEffect(() => {
    let cancelado = false
    setEstado('cargando')
    obtenerCarta(token)
      .then((datos) => {
        if (cancelado) return
        setCarta(datos)
        setCategoriaActiva(datos.categorias[0]?.id ?? null)
        setEstado('listo')
      })
      .catch((e) => {
        if (cancelado) return
        setError(e.message)
        setEstado('error')
      })
    return () => {
      cancelado = true
    }
  }, [token])

  useEffect(() => {
    document.title = carta?.nombreRestaurante ? `${carta.nombreRestaurante} · Carta` : 'Carta digital'
  }, [carta])

  // Resalta en la fila de categorías cuál sección está a la vista mientras
  // el comensal hace scroll -- sin esto, la fila de chips queda "muda" y no
  // ayuda a ubicarse en una carta larga con muchas categorías.
  useEffect(() => {
    if (estado !== 'listo') return
    const secciones = Object.values(seccionesRef.current).filter(Boolean)
    if (secciones.length === 0) return

    const observer = new IntersectionObserver(
      (entradas) => {
        const visible = entradas
          .filter((e) => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0]
        if (visible) {
          setCategoriaActiva(Number(visible.target.dataset.categoriaId))
        }
      },
      { rootMargin: '-35% 0px -55% 0px' },
    )
    secciones.forEach((el) => observer.observe(el))
    return () => observer.disconnect()
  }, [estado, carta])

  function irACategoria(categoriaId) {
    setCategoriaActiva(categoriaId)
    seccionesRef.current[categoriaId]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const variablesMarca = useMemo(
    () => ({
      '--color-acento': carta?.branding?.colorPrimario || '#c1502e',
      '--color-acento-suave': carta?.branding?.colorSecundario || '#d4a24c',
    }),
    [carta],
  )

  if (estado === 'cargando') {
    return (
      <div className="min-h-dvh flex items-center justify-center px-6">
        <div className="flex flex-col items-center gap-3 text-carbon-900/50">
          <div className="w-8 h-8 rounded-full border-2 border-carbon-900/20 border-t-carbon-900/60 animate-spin" />
          <p className="text-sm">Cargando la carta…</p>
        </div>
      </div>
    )
  }

  if (estado === 'error') {
    return (
      <div className="min-h-dvh flex items-center justify-center px-6 text-center">
        <div className="max-w-sm">
          <h1 className="font-display text-xl text-carbon-950 mb-2">No pudimos abrir la carta</h1>
          <p className="text-sm text-carbon-900/60">{error}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-dvh" style={variablesMarca}>
      {/* Encabezado + fila de categorías, ambos sticky: en una carta larga
          el comensal necesita poder saltar de sección sin scrollear a mano
          desde el principio. pt-[env(safe-area-inset-top)] para no quedar
          debajo del notch/Dynamic Island de iPhone. */}
      <header
        className="sticky top-0 z-20 bg-papel-100/95 backdrop-blur-sm border-b border-carbon-950/10"
        style={{ paddingTop: 'env(safe-area-inset-top)' }}
      >
        <div className="px-4 sm:px-6 pt-4 pb-3 flex items-center gap-3">
          {carta.branding?.logoUrl && (
            <img src={carta.branding.logoUrl} alt="" className="w-10 h-10 rounded-full object-cover shrink-0" />
          )}
          <div className="min-w-0">
            <h1 className="font-display text-xl text-carbon-950 truncate">{carta.nombreRestaurante}</h1>
            <p className="text-xs text-carbon-900/40">Carta digital</p>
          </div>
        </div>

        <nav ref={navRef} className="flex gap-2 overflow-x-auto px-4 sm:px-6 pb-3 -mt-1">
          {carta.categorias.map((cat) => (
            <button
              key={cat.id}
              onClick={() => irACategoria(cat.id)}
              className="chip-categoria"
              style={
                categoriaActiva === cat.id
                  ? { backgroundColor: 'var(--color-acento)', color: '#fff' }
                  : { backgroundColor: 'rgba(13,12,11,0.06)', color: 'rgba(13,12,11,0.6)' }
              }
            >
              {cat.nombre}
            </button>
          ))}
        </nav>
      </header>

      <main className="px-4 sm:px-6 py-6 max-w-3xl mx-auto pb-[max(1.5rem,env(safe-area-inset-bottom))]">
        {carta.categorias.length === 0 && (
          <p className="text-center text-sm text-carbon-900/40 py-16">
            Esta carta todavía no tiene platos publicados.
          </p>
        )}

        <div className="space-y-10">
          {carta.categorias.map((cat) => (
            <section
              key={cat.id}
              data-categoria-id={cat.id}
              ref={(el) => {
                seccionesRef.current[cat.id] = el
              }}
              className="scroll-mt-28"
            >
              <h2 className="font-display text-2xl text-carbon-950 mb-4">{cat.nombre}</h2>

              {cat.items.length === 0 ? (
                <p className="text-sm text-carbon-900/40">Sin platos en esta categoría todavía.</p>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {cat.items.map((item) => (
                    <ItemCarta key={item.id} item={item} />
                  ))}
                </div>
              )}
            </section>
          ))}
        </div>

        <p className="text-center text-[11px] text-carbon-900/30 pt-10">
          Carta digital por Mezzalia
        </p>
      </main>
    </div>
  )
}

function ItemCarta({ item }) {
  return (
    <div className={`tarjeta-item animate-fadeInUp ${!item.disponible ? 'opacity-70' : ''}`}>
      <div className="relative aspect-[4/3] bg-papel-200">
        {item.imagenUrl ? (
          <img
            src={item.imagenUrl}
            alt={item.nombre}
            className={`w-full h-full object-cover ${!item.disponible ? 'grayscale-[35%]' : ''}`}
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-carbon-900/20 text-xs">
            Sin foto
          </div>
        )}
        {/* RF-02: agotado se muestra visualmente distinto, NUNCA desaparece
            de la carta -- el comensal debe poder seguir viendo que el plato
            existe, solo que hoy no está disponible. */}
        {!item.disponible && (
          <div className="absolute inset-0 flex items-center justify-center bg-carbon-950/40">
            <span className="bg-carbon-950/80 text-white text-xs font-medium px-3 py-1 rounded-full tracking-wide">
              Agotado
            </span>
          </div>
        )}
      </div>
      <div className="p-4">
        <div className="flex items-start justify-between gap-2">
          <h3 className="font-medium text-carbon-950 text-sm">{item.nombre}</h3>
          <span
            className="text-sm font-semibold whitespace-nowrap"
            style={{ color: 'var(--color-acento)' }}
          >
            {formatoPrecio.format(item.precio)}
          </span>
        </div>
        {item.descripcion && (
          <p className="text-xs text-carbon-900/50 mt-1 line-clamp-2">{item.descripcion}</p>
        )}
      </div>
    </div>
  )
}
