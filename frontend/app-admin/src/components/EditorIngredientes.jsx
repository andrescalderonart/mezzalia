import { useEffect, useState } from 'react'
import { useApi, useApiJson } from '../context/AuthContext.jsx'
import SeparadorCapas from '../fotos/SeparadorCapas.jsx'
import { prepararCapa, prepararTarjeta } from '../fotos/prepararFoto.js'

/**
 * Editor de ingredientes de un plato (2026-11-26): las capas que el comensal
 * ve "desarmadas" en la carta y que puede quitar ("sin cebolla").
 *
 * - Orden = de arriba hacia abajo en la vista APILADA (como se arma una
 *   hamburguesa vista de frente: pan de arriba primero).
 * - Foto por capa: ideal PNG/WEBP sin fondo para que la animacion se vea
 *   limpia; una foto con fondo blanco tambien sirve sobre la carta clara.
 * - "Se puede quitar": el pan o la carne normalmente no.
 */
export default function EditorIngredientes({ item, onCerrar, onPresentacionCambiada }) {
  const apiJson = useApiJson()
  const apiFetch = useApi()
  const [ingredientes, setIngredientes] = useState([])
  const [presentacion, setPresentacion] = useState(item.presentacionIngredientes || 'APILADO')
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [nuevoNombre, setNuevoNombre] = useState('')
  const [nuevoRemovible, setNuevoRemovible] = useState(true)
  const [ocupadoId, setOcupadoId] = useState(null)

  const base = `/api/admin/menu/items/${item.id}`
  // "Fotos fáciles": imagen del plato desarmado a separar en capas (o null).
  const [archivoSeparador, setArchivoSeparador] = useState(null)
  const [progreso, setProgreso] = useState('')

  useEffect(() => {
    apiJson(`${base}/ingredientes`)
      .then(setIngredientes)
      .catch((e) => setError(e.message))
      .finally(() => setCargando(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.id])

  async function ejecutar(id, accion) {
    setOcupadoId(id)
    setError('')
    try {
      await accion()
    } catch (e) {
      setError(e.message)
    } finally {
      setOcupadoId(null)
    }
  }

  function agregar(e) {
    e.preventDefault()
    if (!nuevoNombre.trim()) return
    ejecutar('nuevo', async () => {
      const creado = await apiJson(`${base}/ingredientes`, {
        method: 'POST',
        body: { nombre: nuevoNombre.trim(), removible: nuevoRemovible },
      })
      setIngredientes((prev) => [...prev, creado])
      setNuevoNombre('')
      setNuevoRemovible(true)
    })
  }

  function actualizar(ing, cambios) {
    const nuevo = { ...ing, ...cambios }
    if (!nuevo.nombre.trim()) return
    ejecutar(ing.id, async () => {
      const guardado = await apiJson(`/api/admin/menu/ingredientes/${ing.id}`, {
        method: 'PUT',
        body: { nombre: nuevo.nombre.trim(), removible: nuevo.removible },
      })
      setIngredientes((prev) => prev.map((i) => (i.id === ing.id ? guardado : i)))
    })
  }

  function eliminar(ing) {
    if (!window.confirm(`¿Quitar "${ing.nombre}" de los ingredientes de este plato?`)) return
    ejecutar(ing.id, async () => {
      await apiJson(`/api/admin/menu/ingredientes/${ing.id}`, { method: 'DELETE' })
      setIngredientes((prev) => prev.filter((i) => i.id !== ing.id))
    })
  }

  function mover(indice, delta) {
    const destino = indice + delta
    if (destino < 0 || destino >= ingredientes.length) return
    const ids = ingredientes.map((i) => i.id)
    ;[ids[indice], ids[destino]] = [ids[destino], ids[indice]]
    ejecutar(ingredientes[indice].id, async () => {
      setIngredientes(await apiJson(`${base}/ingredientes/orden`, { method: 'PUT', body: { ids } }))
    })
  }

  async function subirImagenDe(ingredienteId, archivo) {
    const formData = new FormData()
    formData.append('archivo', archivo)
    const resp = await apiFetch(`/api/admin/menu/ingredientes/${ingredienteId}/imagen`, { method: 'POST', body: formData })
    const datos = await resp.json().catch(() => null)
    if (!resp.ok) throw new Error(datos?.mensaje || 'No se pudo subir la foto')
    return datos
  }

  function subirFoto(ing, archivo) {
    if (!archivo) return
    ejecutar(ing.id, async () => {
      // Antes de subirla: sin fondo para una capa apilada, recortada sobre
      // blanco para una tarjeta esparcida; y aligerada en ambos casos.
      const lista = presentacion === 'APILADO' ? await prepararCapa(archivo) : await prepararTarjeta(archivo)
      const datos = await subirImagenDe(ing.id, lista)
      setIngredientes((prev) => prev.map((i) => (i.id === ing.id ? datos : i)))
    })
  }

  /** Desde el separador: crea las capas en orden (y opcionalmente borra las de antes). */
  async function crearCapasDesdeFoto(capas, reemplazar) {
    setError('')
    try {
      if (reemplazar) {
        for (const ing of ingredientes) {
          setProgreso(`Quitando capas anteriores…`)
          await apiJson(`/api/admin/menu/ingredientes/${ing.id}`, { method: 'DELETE' })
        }
      }
      const creadas = []
      for (let i = 0; i < capas.length; i++) {
        setProgreso(`Subiendo capa ${i + 1} de ${capas.length}…`)
        const creada = await apiJson(`${base}/ingredientes`, {
          method: 'POST',
          body: { nombre: capas[i].nombre, removible: capas[i].removible },
        })
        creadas.push(await subirImagenDe(creada.id, capas[i].archivo))
      }
      setIngredientes(reemplazar ? creadas : await apiJson(`${base}/ingredientes`))
      setArchivoSeparador(null)
    } catch (e) {
      setError(e.message)
      setArchivoSeparador(null)
      setIngredientes(await apiJson(`${base}/ingredientes`).catch(() => ingredientes))
    } finally {
      setProgreso('')
    }
  }

  function cambiarPresentacion(valor) {
    if (valor === presentacion) return
    ejecutar('presentacion', async () => {
      const actualizado = await apiJson(`${base}/presentacion`, { method: 'PATCH', body: { presentacion: valor } })
      setPresentacion(actualizado.presentacionIngredientes)
      onPresentacionCambiada?.(actualizado)
    })
  }

  return (
    <div className="fixed inset-0 bg-carbon-950/80 backdrop-blur-sm flex items-center justify-center px-4 py-6 z-50 animate-fadeInUp">
      <div className="card w-full max-w-lg p-6 max-h-[90dvh] overflow-y-auto" role="dialog" aria-modal="true" aria-label={`Ingredientes de ${item.nombre}`}>
        <div className="flex items-start justify-between gap-3 mb-1">
          <h2 className="font-display font-semibold text-xl text-crema">Ingredientes · {item.nombre}</h2>
          <button onClick={onCerrar} className="text-crema/50 hover:text-crema text-sm" aria-label="Cerrar">
            ✕
          </button>
        </div>
        <p className="text-xs text-crema/40 mb-5">
          El comensal ve el plato "desarmado" en estas capas y puede quitar las que marques como quitables. La
          cocina recibe "SIN …".
        </p>

        <fieldset className="mb-5">
          <legend className="label-carbon">Cómo se muestra</legend>
          <div className="flex gap-2">
            {[
              ['APILADO', 'Apilado', 'Hamburguesas, sándwiches'],
              ['ESPARCIDO', 'Esparcido', 'Bandejas, ensaladas'],
            ].map(([valor, titulo, ejemplo]) => (
              <button
                key={valor}
                type="button"
                aria-pressed={presentacion === valor}
                onClick={() => cambiarPresentacion(valor)}
                className={`flex-1 rounded-lg border px-3 py-2 text-left transition-colors duration-150 ${
                  presentacion === valor ? 'border-dorado bg-dorado/10' : 'border-carbon-600 hover:border-crema/30'
                }`}
              >
                <span className="block text-sm text-crema">{titulo}</span>
                <span className="block text-[11px] text-crema/40">{ejemplo}</span>
              </button>
            ))}
          </div>
        </fieldset>

        {presentacion === 'APILADO' && (
          <div className="mb-5 rounded-xl border border-dashed border-dorado/40 bg-dorado/5 p-3">
            <label className="flex items-center justify-between gap-3 cursor-pointer">
              <span>
                <span className="block text-sm text-crema">📸 Crear capas desde una foto</span>
                <span className="block text-[11px] text-crema/50">
                  Sube una imagen del plato desarmado (fondo liso) y separamos las capas solas.
                </span>
              </span>
              <span className="btn-secundario text-xs py-1.5 px-3 whitespace-nowrap">Elegir foto</span>
              <input
                type="file"
                accept="image/png,image/webp,image/jpeg"
                className="sr-only"
                aria-label="Foto del plato desarmado"
                onChange={(e) => {
                  setArchivoSeparador(e.target.files[0] || null)
                  e.target.value = ''
                }}
              />
            </label>
            {progreso && <p className="text-xs text-dorado mt-2" role="status">{progreso}</p>}
          </div>
        )}

        {archivoSeparador && (
          <SeparadorCapas
            archivo={archivoSeparador}
            capasActuales={ingredientes.length}
            onCancelar={() => setArchivoSeparador(null)}
            onCrear={crearCapasDesdeFoto}
          />
        )}

        {error && (
          <div className="mb-4 text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2">{error}</div>
        )}

        <p className="label-carbon">
          Capas {presentacion === 'APILADO' ? '(de arriba hacia abajo)' : ''}
        </p>
        {cargando ? (
          <div className="h-24 rounded-xl bg-carbon-800 animate-pulseSoft" />
        ) : ingredientes.length === 0 ? (
          <p className="text-sm text-crema/40 py-4">Este plato todavía no tiene ingredientes.</p>
        ) : (
          <ul className="space-y-2 mb-4">
            {ingredientes.map((ing, i) => (
              <li key={ing.id} className={`flex items-center gap-3 rounded-xl border border-carbon-700 bg-carbon-900 p-2 ${ocupadoId === ing.id ? 'opacity-60' : ''}`}>
                <label className="relative w-14 h-14 shrink-0 rounded-lg bg-carbon-800 overflow-hidden cursor-pointer group" title="Cambiar foto de la capa">
                  {ing.imagenUrl ? (
                    <img src={ing.imagenUrl} alt="" className="w-full h-full object-contain" />
                  ) : (
                    <span className="absolute inset-0 flex items-center justify-center text-[10px] text-crema/40 text-center leading-tight">
                      Subir
                      <br />
                      foto
                    </span>
                  )}
                  <span className="absolute inset-0 bg-carbon-950/60 opacity-0 group-hover:opacity-100 flex items-center justify-center text-[10px] text-crema">
                    Cambiar
                  </span>
                  <input
                    type="file"
                    accept="image/png,image/webp,image/jpeg"
                    className="sr-only"
                    onChange={(e) => subirFoto(ing, e.target.files[0])}
                    aria-label={`Foto de ${ing.nombre}`}
                  />
                </label>

                <div className="flex-1 min-w-0">
                  <input
                    className="input-carbon py-1.5 text-sm"
                    defaultValue={ing.nombre}
                    aria-label="Nombre del ingrediente"
                    onBlur={(e) => e.target.value.trim() !== ing.nombre && actualizar(ing, { nombre: e.target.value })}
                  />
                  <label className="mt-1 flex items-center gap-1.5 text-[11px] text-crema/60">
                    <input
                      type="checkbox"
                      checked={ing.removible}
                      onChange={(e) => actualizar(ing, { removible: e.target.checked })}
                    />
                    Se puede quitar
                  </label>
                </div>

                <div className="flex flex-col gap-1">
                  <button onClick={() => mover(i, -1)} disabled={i === 0} className="text-crema/50 hover:text-crema disabled:opacity-20 text-xs" aria-label={`Subir ${ing.nombre}`}>
                    ▲
                  </button>
                  <button onClick={() => mover(i, 1)} disabled={i === ingredientes.length - 1} className="text-crema/50 hover:text-crema disabled:opacity-20 text-xs" aria-label={`Bajar ${ing.nombre}`}>
                    ▼
                  </button>
                </div>
                <button onClick={() => eliminar(ing)} className="text-crema/30 hover:text-red-300 text-xs px-1" aria-label={`Eliminar ${ing.nombre}`}>
                  ✕
                </button>
              </li>
            ))}
          </ul>
        )}

        <form onSubmit={agregar} className="flex flex-wrap items-center gap-2 border-t border-carbon-700 pt-4">
          <input
            className="input-carbon flex-1 min-w-[10rem]"
            placeholder="Nuevo ingrediente (ej. Cebolla)"
            value={nuevoNombre}
            onChange={(e) => setNuevoNombre(e.target.value)}
            maxLength={80}
          />
          <label className="flex items-center gap-1.5 text-xs text-crema/60">
            <input type="checkbox" checked={nuevoRemovible} onChange={(e) => setNuevoRemovible(e.target.checked)} />
            Se puede quitar
          </label>
          <button type="submit" disabled={ocupadoId === 'nuevo' || !nuevoNombre.trim()} className="btn-primario text-xs py-2">
            Agregar
          </button>
        </form>
      </div>
    </div>
  )
}
