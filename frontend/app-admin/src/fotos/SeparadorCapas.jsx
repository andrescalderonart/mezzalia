import { useEffect, useMemo, useRef, useState } from 'react'
import { aArchivo, aDataUrl, cargarImagen } from './canvas.js'
import {
  cortarEn,
  detectarFranjas,
  muestrearFondo,
  perfilFilas,
  recortarFranjaTransparente,
  sugerirCortes,
  unirConSiguiente,
} from './procesarImagen.js'

/**
 * "Fotos fáciles" (2026-11-27): el dueño sube UNA imagen del plato desarmado
 * (capas flotando una sobre otra, fondo liso) y aquí se separa sola en capas
 * con fondo transparente, listas para la vista apilada de la carta.
 *
 * La detección busca filas vacías entre capas. Si dos capas se tocan, salen
 * juntas: el dueño toca la foto donde se separan (o usa "Separar solas" en
 * una franja muy alta) y, si sobra un corte, une la capa con la de abajo.
 *
 * Todas las capas se exportan con la MISMA escala, para que la carta respete
 * sus tamaños relativos (el alioli no queda del tamaño del pan).
 */
const ANCHO_FINAL = 600 // px del plato completo en la carta; suficiente para un celular

export default function SeparadorCapas({ archivo, capasActuales = 0, onCancelar, onCrear }) {
  const [imagen, setImagen] = useState(null)
  const [fondo, setFondo] = useState(null)
  const [cuenta, setCuenta] = useState(null)
  const [franjas, setFranjas] = useState([])
  const [nombres, setNombres] = useState([])
  const [fijas, setFijas] = useState([])
  const [vistaUrl, setVistaUrl] = useState('')
  const [error, setError] = useState('')
  const [creando, setCreando] = useState(false)
  const [reemplazar, setReemplazar] = useState(capasActuales > 0)
  const imgRef = useRef(null)

  useEffect(() => {
    let cancelado = false
    cargarImagen(archivo)
      .then((img) => {
        if (cancelado) return
        const f = muestrearFondo(img)
        const detectadas = detectarFranjas(img, f)
        setImagen(img)
        setFondo(f)
        setCuenta(perfilFilas(img, f))
        setFranjas(detectadas)
        setNombres(detectadas.map(() => ''))
        // Lo normal: el pan de arriba y el de abajo no se quitan.
        setFijas(detectadas.map((_, i) => i === 0 || i === detectadas.length - 1))
        setVistaUrl(aDataUrl(img, Math.min(1, 900 / img.height)))
      })
      .catch(() => !cancelado && setError('No se pudo leer la imagen. Prueba con JPG, PNG o WEBP.'))
    return () => {
      cancelado = true
    }
  }, [archivo])

  // Miniatura de cada capa tal como quedará (fondo transparente).
  const miniaturas = useMemo(() => {
    if (!imagen || !fondo) return []
    return franjas.map((f) => {
      const capa = recortarFranjaTransparente(imagen, f, fondo)
      return capa ? aDataUrl(capa, Math.min(1, 72 / capa.height, 160 / capa.width)) : ''
    })
  }, [imagen, fondo, franjas])

  const altoMediano = useMemo(() => {
    const altos = franjas.map((f) => f.y1 - f.y0).sort((a, b) => a - b)
    return altos[Math.floor(altos.length / 2)] || 0
  }, [franjas])

  function aplicarCorte(y) {
    const i = franjas.findIndex((f) => y > f.y0 && y < f.y1)
    if (i < 0) return
    setFranjas(cortarEn(franjas, y))
    setNombres((prev) => [...prev.slice(0, i + 1), '', ...prev.slice(i + 1)])
    setFijas((prev) => [...prev.slice(0, i + 1), false, ...prev.slice(i + 1)])
  }

  function tocarFoto(e) {
    if (!imagen || !imgRef.current) return
    const rect = imgRef.current.getBoundingClientRect()
    aplicarCorte(Math.round(((e.clientY - rect.top) / rect.height) * imagen.height))
  }

  function separarSolas(i) {
    const cortes = sugerirCortes(cuenta, franjas[i])
    // De abajo hacia arriba para que los índices sigan valiendo.
    let nuevas = franjas
    let nuevosNombres = nombres
    let nuevasFijas = fijas
    for (const y of [...cortes].reverse()) {
      nuevas = cortarEn(nuevas, y)
      nuevosNombres = [...nuevosNombres.slice(0, i + 1), '', ...nuevosNombres.slice(i + 1)]
      nuevasFijas = [...nuevasFijas.slice(0, i + 1), false, ...nuevasFijas.slice(i + 1)]
    }
    setFranjas(nuevas)
    setNombres(nuevosNombres)
    setFijas(nuevasFijas)
  }

  function unir(i) {
    setFranjas(unirConSiguiente(franjas, i))
    setNombres((prev) => [...prev.slice(0, i + 1), ...prev.slice(i + 2)])
    setFijas((prev) => [...prev.slice(0, i + 1), ...prev.slice(i + 2)])
  }

  async function crear() {
    setCreando(true)
    setError('')
    try {
      const escala = Math.min(1, ANCHO_FINAL / imagen.width)
      const capas = []
      for (let i = 0; i < franjas.length; i++) {
        const recorte = recortarFranjaTransparente(imagen, franjas[i], fondo)
        if (!recorte) continue
        const nombre = nombres[i].trim() || `Capa ${i + 1}`
        capas.push({
          nombre,
          removible: !fijas[i],
          archivo: await aArchivo(recorte, { escala, transparente: true, nombre: `capa-${i + 1}` }),
        })
      }
      await onCrear(capas, reemplazar)
    } catch (e) {
      setError(e.message || 'No se pudieron crear las capas.')
      setCreando(false)
    }
  }

  const sinNombre = nombres.some((n) => !n.trim())

  return (
    <div className="fixed inset-0 z-[60] bg-carbon-950/90 backdrop-blur-sm flex items-center justify-center p-3 sm:p-6">
      <div
        className="card w-full max-w-4xl max-h-[94dvh] overflow-y-auto p-5"
        role="dialog"
        aria-modal="true"
        aria-label="Separar capas desde una foto"
      >
        <div className="flex items-start justify-between gap-3 mb-1">
          <h2 className="font-display text-xl text-crema">Separar capas de la foto</h2>
          <button onClick={onCancelar} disabled={creando} className="text-crema/50 hover:text-crema text-sm" aria-label="Cerrar">
            ✕
          </button>
        </div>
        <p className="text-xs text-crema/50 mb-4">
          Encontramos las capas solas. Si dos ingredientes quedaron juntos, <strong>toca la foto donde se separan</strong>.
          Ponle nombre a cada capa y marca las que no se pueden quitar.
        </p>

        {error && (
          <div className="mb-4 text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2">{error}</div>
        )}

        {!imagen ? (
          !error && <div className="h-64 rounded-xl bg-carbon-800 animate-pulseSoft" aria-label="Analizando la foto" />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-5">
            {/* La foto con las capas detectadas marcadas encima. */}
            <div className="self-start mx-auto max-w-[320px] md:max-w-none w-full">
              {/* Contenedor solo de la foto: los recuadros se posicionan en %
                  de ESTA caja, así que no puede incluir el texto de abajo. */}
              <div className="relative">
              <img
                ref={imgRef}
                src={vistaUrl}
                alt="Foto del plato desarmado"
                onClick={tocarFoto}
                className="w-full rounded-lg cursor-crosshair select-none bg-white"
                draggable="false"
              />
              {franjas.map((f, i) => (
                <div
                  key={`${f.y0}-${f.y1}`}
                  className="absolute inset-x-0 border-2 border-dorado/80 rounded-md pointer-events-none"
                  style={{ top: `${(f.y0 / imagen.height) * 100}%`, height: `${((f.y1 - f.y0 + 1) / imagen.height) * 100}%` }}
                >
                  <span className="absolute -left-2 top-1/2 -translate-y-1/2 -translate-x-full text-[11px] font-bold text-dorado">
                    {i + 1}
                  </span>
                </div>
              ))}
              </div>
              <p className="text-[11px] text-crema/40 mt-2 text-center">Toca la foto para agregar un corte.</p>
            </div>

            {/* Una fila por capa. */}
            <ol className="space-y-2">
              {franjas.map((f, i) => {
                const muyAlta = altoMediano > 0 && f.y1 - f.y0 > altoMediano * 1.6 && cuenta && sugerirCortes(cuenta, f).length > 0
                return (
                  <li key={`${f.y0}-${f.y1}`} className="rounded-xl border border-carbon-700 bg-carbon-900 p-2">
                    <div className="flex items-center gap-3">
                      <span className="text-xs font-bold text-dorado w-4 text-right">{i + 1}</span>
                      <div className="w-20 h-12 shrink-0 rounded-md bg-[repeating-conic-gradient(#2a2723_0%_25%,#1d1b18_0%_50%)] bg-[length:12px_12px] flex items-center justify-center overflow-hidden">
                        {miniaturas[i] && <img src={miniaturas[i]} alt="" className="max-w-full max-h-full object-contain" />}
                      </div>
                      <div className="flex-1 min-w-0">
                        <input
                          className="input-carbon py-1.5 text-sm"
                          placeholder={`Nombre (ej. ${['Pan', 'Lechuga', 'Tomate', 'Queso', 'Carne', 'Cebolla'][i % 6]})`}
                          value={nombres[i]}
                          onChange={(e) => setNombres((prev) => prev.map((n, k) => (k === i ? e.target.value : n)))}
                          maxLength={80}
                          aria-label={`Nombre de la capa ${i + 1}`}
                        />
                        <label className="mt-1 flex items-center gap-1.5 text-[11px] text-crema/60">
                          <input
                            type="checkbox"
                            checked={!fijas[i]}
                            onChange={(e) => setFijas((prev) => prev.map((v, k) => (k === i ? !e.target.checked : v)))}
                          />
                          Se puede quitar
                        </label>
                      </div>
                    </div>
                    {(muyAlta || i < franjas.length - 1) && (
                      <div className="flex gap-3 mt-1.5 pl-[7.5rem] text-[11px]">
                        {muyAlta && (
                          <button onClick={() => separarSolas(i)} className="text-dorado hover:underline">
                            Parece que aquí hay varias capas: separarlas
                          </button>
                        )}
                        {i < franjas.length - 1 && (
                          <button onClick={() => unir(i)} className="text-crema/40 hover:text-crema">
                            Unir con la de abajo
                          </button>
                        )}
                      </div>
                    )}
                  </li>
                )
              })}
              {franjas.length === 0 && (
                <li className="text-sm text-crema/50">
                  No encontramos capas. Usa una foto con fondo liso (blanco o gris claro) y espacio entre los ingredientes.
                </li>
              )}
            </ol>
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-carbon-700 mt-5 pt-4">
          {capasActuales > 0 ? (
            <label className="flex items-center gap-2 text-xs text-crema/70">
              <input type="checkbox" checked={reemplazar} onChange={(e) => setReemplazar(e.target.checked)} />
              Reemplazar las {capasActuales} capas que ya tiene el plato
            </label>
          ) : (
            <span className="text-xs text-crema/40">{sinNombre ? 'Las capas sin nombre se llamarán "Capa 1", "Capa 2"…' : ''}</span>
          )}
          <div className="flex gap-2">
            <button onClick={onCancelar} disabled={creando} className="btn-secundario">
              Cancelar
            </button>
            <button onClick={crear} disabled={creando || franjas.length === 0} className="btn-primario">
              {creando ? 'Creando capas…' : `Crear ${franjas.length} capas`}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
