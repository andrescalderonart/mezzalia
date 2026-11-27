import { useEffect, useRef, useState } from 'react'
import { nombresQuitados, textoSin } from './carrito.js'

/**
 * El plato "desarmado" (2026-11-26): cada ingrediente es una capa con su
 * foto. Al abrir, las capas se separan (APILADO, como una hamburguesa vista
 * de frente) o se esparcen (ESPARCIDO, para bandejas y ensaladas). Tocar un
 * ingrediente quitable lo saca del plato ("sin cebolla"); tocar uno que no se
 * puede quitar (el pan) solo tiembla.
 *
 * No envia nada por si sola: "Agregar al pedido" devuelve los ids quitados y
 * la carta los agrega al carrito como una linea aparte.
 */

const formatoPrecio = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })

// Alto de cada capa y separacion entre capas en la vista apilada, relativos
// al alto de la pantalla para que una hamburguesa de 8 capas quepa abierta
// en un celular sin hacer scroll. "Cerrado" (antes de la animacion) cada
// capa se corre hacia el centro un 80% de esa distancia: arrancan casi
// encimadas, como el plato armado, y se separan.
const ALTO_CAPA = 'clamp(40px, 7vh, 96px)'
const SEPARACION = 'clamp(10px, 2.4vh, 26px)'

function prefiereMenosMovimiento() {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

export default function VistaIngredientes({ item, onCerrar, onAgregar }) {
  const ingredientes = item.ingredientes || []
  const apilado = item.presentacionIngredientes !== 'ESPARCIDO'
  const sinMovimiento = prefiereMenosMovimiento()
  const [abierto, setAbierto] = useState(sinMovimiento)
  const [quitados, setQuitados] = useState([])
  const [sacudiendoId, setSacudiendoId] = useState(null)
  const [mensaje, setMensaje] = useState('')
  const cerrarRef = useRef(null)
  const mensajeTimerRef = useRef(null)
  // Ancho natural de cada foto de capa (se conoce al cargar). Las capas de un
  // plato se exportan a la MISMA escala, asi que mostrarlas proporcionales a
  // la mas ancha respeta los tamanos reales (el alioli no sale del tamano
  // del pan). Mientras cargan se usa el alto fijo de antes.
  const [anchos, setAnchos] = useState({})
  const anchoMax = Math.max(0, ...Object.values(anchos))

  // Abre con animacion despues del primer pintado (si no, no hay transicion).
  useEffect(() => {
    if (sinMovimiento) return undefined
    const id = requestAnimationFrame(() => requestAnimationFrame(() => setAbierto(true)))
    return () => cancelAnimationFrame(id)
  }, [sinMovimiento])

  // Escape cierra; el fondo no se desplaza mientras esta abierta.
  useEffect(() => {
    cerrarRef.current?.focus()
    const onKey = (e) => e.key === 'Escape' && onCerrar()
    document.addEventListener('keydown', onKey)
    const overflowAnterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflowAnterior
      clearTimeout(mensajeTimerRef.current)
    }
  }, [onCerrar])

  const hayQuitables = ingredientes.some((i) => i.removible)
  const sin = textoSin(nombresQuitados(item, quitados))

  function tocar(ing) {
    // Plato informativo (nada se puede quitar, p. ej. un sancocho): tocar no
    // hace nada; la vista solo muestra lo que trae.
    if (!hayQuitables) return
    if (!ing.removible) {
      setSacudiendoId(null)
      requestAnimationFrame(() => setSacudiendoId(ing.id))
      setMensaje(`${ing.nombre} no se puede quitar de este plato.`)
      clearTimeout(mensajeTimerRef.current)
      mensajeTimerRef.current = setTimeout(() => setMensaje(''), 2500)
      return
    }
    setMensaje('')
    setQuitados((prev) => (prev.includes(ing.id) ? prev.filter((id) => id !== ing.id) : [...prev, ing.id]))
  }

  const centro = (ingredientes.length - 1) / 2

  return (
    <div
      className="fixed inset-0 z-50 flex flex-col bg-papel-100"
      role="dialog"
      aria-modal="true"
      aria-label={`Ingredientes de ${item.nombre}`}
      style={{ paddingTop: 'env(safe-area-inset-top)' }}
    >
      <header className="flex items-start justify-between gap-3 px-4 sm:px-6 pt-4 pb-2">
        <div className="min-w-0">
          <h2 className="font-display text-2xl text-carbon-950 truncate">{item.nombre}</h2>
          <p className="text-xs text-carbon-900/50">
            {hayQuitables ? 'Toca un ingrediente para quitarlo de tu plato.' : 'Así viene este plato.'}
          </p>
        </div>
        <button
          ref={cerrarRef}
          type="button"
          onClick={onCerrar}
          aria-label="Cerrar ingredientes"
          className="w-9 h-9 shrink-0 rounded-full bg-carbon-950/5 flex items-center justify-center text-carbon-950"
        >
          ✕
        </button>
      </header>

      <div className="flex-1 overflow-y-auto px-4 sm:px-6 py-4">
        {apilado ? (
          <ul className="max-w-sm mx-auto flex flex-col items-center" style={{ gap: SEPARACION }}>
            {ingredientes.map((ing, i) => {
              const quitado = quitados.includes(ing.id)
              const pasos = abierto ? 0 : (centro - i) * 0.8
              return (
                <li
                  key={ing.id}
                  className="w-full"
                  style={{
                    transform: `translateY(calc(${pasos} * (${ALTO_CAPA} + ${SEPARACION})))`,
                    transition: sinMovimiento ? 'none' : `transform 700ms cubic-bezier(.2,.8,.2,1) ${i * 45}ms`,
                    zIndex: ingredientes.length - i,
                    position: 'relative',
                  }}
                >
                  <Capa
                    ing={ing}
                    quitado={quitado}
                    sacudiendo={sacudiendoId === ing.id}
                    onTocar={() => tocar(ing)}
                    apilado
                    sinMovimiento={sinMovimiento}
                    informativo={!hayQuitables}
                    anchoRelativo={anchos[ing.id] && anchoMax ? anchos[ing.id] / anchoMax : null}
                    onCargarFoto={(ancho) => setAnchos((prev) => (prev[ing.id] === ancho ? prev : { ...prev, [ing.id]: ancho }))}
                  />
                </li>
              )
            })}
          </ul>
        ) : (
          <div className="max-w-md mx-auto">
          {/* Esparcido: arriba el plato completo (lo que el cliente va a
              recibir) y debajo lo que trae. Sirve igual para una bandeja con
              partes quitables que para una sopa donde todo viene incluido. */}
          {item.imagenUrl && (
            <img
              src={item.imagenUrl}
              alt={item.nombre}
              className="w-full max-h-[30vh] object-cover rounded-2xl mb-4"
              style={{
                opacity: abierto ? 1 : 0,
                transition: sinMovimiento ? 'none' : 'opacity 400ms ease',
              }}
            />
          )}
          <p className="text-xs font-medium uppercase tracking-wide text-carbon-900/40 mb-2">
            {hayQuitables ? 'Lo que trae' : 'Lo que trae este plato'}
          </p>
          <ul className="grid grid-cols-2 gap-3">
            {ingredientes.map((ing, i) => (
              <li
                key={ing.id}
                style={{
                  opacity: abierto ? 1 : 0,
                  transform: abierto ? 'scale(1)' : 'scale(.6)',
                  transition: sinMovimiento ? 'none' : `opacity 450ms ease ${i * 60}ms, transform 550ms cubic-bezier(.2,.8,.2,1) ${i * 60}ms`,
                }}
              >
                <Capa ing={ing} quitado={quitados.includes(ing.id)} sacudiendo={sacudiendoId === ing.id} onTocar={() => tocar(ing)} sinMovimiento={sinMovimiento} informativo={!hayQuitables} />
              </li>
            ))}
          </ul>
          </div>
        )}
      </div>

      <footer
        className="border-t border-carbon-950/10 bg-papel-100 px-4 sm:px-6 pt-3"
        style={{ paddingBottom: 'max(1rem, env(safe-area-inset-bottom))' }}
      >
        <p className="min-h-[1.25rem] text-sm text-center mb-2" role="status" aria-live="polite">
          {mensaje ? (
            <span className="text-carbon-900/60">{mensaje}</span>
          ) : sin ? (
            <span className="font-semibold text-red-700 first-letter:uppercase inline-block">{sin}</span>
          ) : (
            <span className="text-carbon-900/40">Con todos sus ingredientes</span>
          )}
        </p>
        {item.disponible ? (
          <button
            type="button"
            onClick={() => onAgregar(quitados)}
            className="w-full rounded-xl py-3 text-sm font-semibold text-white"
            style={{ backgroundColor: 'var(--color-acento)' }}
          >
            Agregar al pedido · {formatoPrecio.format(item.precio)}
          </button>
        ) : (
          <p className="text-center text-sm text-carbon-900/50 py-3">Este plato está agotado.</p>
        )}
      </footer>
    </div>
  )
}

function Capa({ ing, quitado, sacudiendo, onTocar, apilado = false, sinMovimiento, informativo = false, anchoRelativo = null, onCargarFoto }) {
  const conFoto = Boolean(ing.imagenUrl)
  // Con foto, la etiqueta nombra el ingrediente. Sin foto el nombre ya va en
  // la pastilla, asi que la etiqueta solo dice el estado (o no aparece).
  // "Incluido" marca lo que no se puede quitar, solo cuando hay algo que SI
  // se puede quitar; en un plato informativo (sancocho) no hace falta.
  const marcaIncluido = !ing.removible && !informativo
  const etiqueta = quitado
    ? `Sin ${ing.nombre.toLowerCase()}`
    : conFoto
      ? `${ing.nombre}${marcaIncluido ? ' · incluido' : ''}`
      : marcaIncluido
        ? 'incluido'
        : null
  return (
    <button
      type="button"
      onClick={onTocar}
      aria-pressed={ing.removible && !informativo ? quitado : undefined}
      aria-label={
        informativo
          ? ing.nombre
          : ing.removible
            ? `${ing.nombre}${quitado ? ', quitado. Toca para volver a ponerlo' : '. Toca para quitarlo'}`
            : `${ing.nombre}, incluido, no se puede quitar`
      }
      tabIndex={informativo ? -1 : undefined}
      className={`relative w-full flex items-center justify-center rounded-2xl outline-offset-2 ${informativo ? 'cursor-default' : ''} ${sacudiendo ? 'animate-sacudir' : ''} ${
        apilado ? '' : `flex-col border border-carbon-950/5 p-3 ${conFoto ? 'bg-white' : 'bg-white/60 min-h-[5.5rem]'}`
      }`}
    >
      {/* En apilado, la capa se centra en el 74% izquierdo para dejar la
          columna derecha a la etiqueta (si no, nombres largos tapan la foto). */}
      <span
        className={`flex items-center justify-center w-full ${apilado ? 'pr-[26%]' : ''}`}
        style={{
          opacity: quitado ? 0.22 : 1,
          filter: quitado ? 'grayscale(1)' : 'none',
          transform: quitado ? (apilado ? 'translateX(18%) scale(.9)' : 'scale(.85)') : 'none',
          transition: sinMovimiento ? 'none' : 'opacity 300ms ease, transform 350ms cubic-bezier(.2,.8,.2,1), filter 300ms ease',
        }}
      >
        {ing.imagenUrl ? (
          <img
            src={ing.imagenUrl}
            alt=""
            draggable="false"
            className={apilado ? 'object-contain select-none' : 'w-full aspect-[4/3] object-contain select-none'}
            style={
              apilado
                ? anchoRelativo
                  ? { width: `${Math.round(anchoRelativo * 100)}%`, height: 'auto', maxHeight: 'clamp(48px, 13vh, 150px)' }
                  : { height: ALTO_CAPA, width: 'auto', maxWidth: '100%' }
                : undefined
            }
            onLoad={(e) => onCargarFoto?.(e.currentTarget.naturalWidth)}
          />
        ) : (
          <span className={`rounded-full bg-carbon-950/5 text-carbon-950 font-medium ${apilado ? 'px-5 py-3 text-base' : 'px-3 py-2 text-sm'}`}>
            {ing.nombre}
          </span>
        )}
      </span>

      {/* Etiqueta del ingrediente: a la derecha en apilado, debajo en esparcido. */}
      {etiqueta && (
      <span
        className={`text-[11px] font-semibold rounded-full px-2 py-0.5 whitespace-nowrap ${
          apilado ? 'absolute right-0 top-1/2 -translate-y-1/2' : 'mt-1.5'
        } ${quitado ? 'bg-red-700 text-white' : ing.removible ? 'bg-white/95 text-carbon-900/75 shadow-sm' : 'bg-white/80 text-carbon-900/45'}`}
      >
        {etiqueta}
      </span>
      )}
    </button>
  )
}
