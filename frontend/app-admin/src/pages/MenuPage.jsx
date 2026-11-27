import { useEffect, useState } from 'react'
import { useAuth, useApi, useApiJson } from '../context/AuthContext.jsx'
import EditorIngredientes from '../components/EditorIngredientes.jsx'
import { prepararFotoPlato } from '../fotos/prepararFoto.js'

const formatoPrecio = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
})

export default function MenuPage() {
  const { usuario } = useAuth()
  const apiJson = useApiJson()
  const apiFetch = useApi()
  const puedeEditar = usuario?.rol === 'ADMIN_RESTAURANTE'

  const [categorias, setCategorias] = useState([])
  const [categoriaId, setCategoriaId] = useState(null)
  const [items, setItems] = useState([])
  const [cargandoCategorias, setCargandoCategorias] = useState(true)
  const [cargandoItems, setCargandoItems] = useState(false)
  const [error, setError] = useState('')

  const [nombreNuevaCategoria, setNombreNuevaCategoria] = useState('')
  const [creandoCategoria, setCreandoCategoria] = useState(false)

  const [modal, setModal] = useState(null) // { modo: 'crear' | 'editar', item?: {...} }
  const [itemIngredientes, setItemIngredientes] = useState(null)

  async function cargarCategorias() {
    setCargandoCategorias(true)
    setError('')
    try {
      const datos = await apiJson('/api/admin/menu/categorias')
      setCategorias(datos)
      if (datos.length && !categoriaId) {
        setCategoriaId(datos[0].id)
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setCargandoCategorias(false)
    }
  }

  async function cargarItems(idCategoria) {
    if (!idCategoria) {
      setItems([])
      return
    }
    setCargandoItems(true)
    setError('')
    try {
      const datos = await apiJson(`/api/admin/menu/categorias/${idCategoria}/items`)
      setItems(datos)
    } catch (e) {
      setError(e.message)
    } finally {
      setCargandoItems(false)
    }
  }

  useEffect(() => {
    cargarCategorias()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    cargarItems(categoriaId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categoriaId])

  async function onCrearCategoria(e) {
    e.preventDefault()
    if (!nombreNuevaCategoria.trim()) return
    setCreandoCategoria(true)
    setError('')
    try {
      const categoria = await apiJson('/api/admin/menu/categorias', {
        method: 'POST',
        body: { nombre: nombreNuevaCategoria.trim(), orden: categorias.length },
      })
      setNombreNuevaCategoria('')
      setCategorias((prev) => [...prev, categoria])
      setCategoriaId(categoria.id)
    } catch (e) {
      setError(e.message)
    } finally {
      setCreandoCategoria(false)
    }
  }

  async function onGuardarItem({ nombre, descripcion, precio, archivo, itemExistente }) {
    setError('')
    try {
      let item
      if (itemExistente) {
        item = await apiJson(`/api/admin/menu/items/${itemExistente.id}`, {
          method: 'PUT',
          body: { nombre, descripcion, precio, imagenUrl: itemExistente.imagenUrl },
        })
      } else {
        item = await apiJson('/api/admin/menu/items', {
          method: 'POST',
          body: { categoriaId, nombre, descripcion, precio, imagenUrl: null },
        })
      }

      if (archivo) {
        const formData = new FormData()
        // Foto principal: se reduce antes de subirla si es pesada (fotos de
        // celular de 4-8 MB tardan mucho en datos móviles).
        formData.append('archivo', await prepararFotoPlato(archivo))
        const resp = await apiFetch(`/api/admin/menu/items/${item.id}/imagen`, {
          method: 'POST',
          body: formData,
        })
        if (resp.ok) {
          item = await resp.json()
        }
      }

      setModal(null)
      cargarItems(categoriaId)
    } catch (e) {
      setError(e.message)
    }
  }

  async function onCambiarDisponibilidad(item) {
    setError('')
    try {
      await apiJson(`/api/admin/menu/items/${item.id}/disponibilidad`, {
        method: 'PATCH',
        body: { disponible: !item.disponible },
      })
      setItems((prev) =>
        prev.map((i) => (i.id === item.id ? { ...i, disponible: !i.disponible } : i)),
      )
    } catch (e) {
      setError(e.message)
    }
  }

  async function onEliminarItem(item) {
    if (!window.confirm(`¿Eliminar "${item.nombre}" de la carta?`)) return
    setError('')
    try {
      await apiFetch(`/api/admin/menu/items/${item.id}`, { method: 'DELETE' })
      setItems((prev) => prev.filter((i) => i.id !== item.id))
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="font-display font-semibold text-2xl text-crema">Carta</h1>
          <p className="text-sm text-crema/40">Categorías y platos de tu restaurante</p>
        </div>
      </div>

      {error && (
        <div className="mb-4 text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2 animate-fadeInUp">
          {error}
        </div>
      )}

      {/* En pantallas angostas las categorías van arriba como una fila de
          chips con scroll horizontal (mismo <ul>, solo cambia flex-direction
          por breakpoint); desde lg se acomodan como sidebar vertical de
          220px, como en escritorio. */}
      <div className="grid grid-cols-1 lg:grid-cols-[220px_1fr] gap-4 lg:gap-6">
        {/* ── Categorías ─────────────────────────────────────────── */}
        <div className="card p-4 lg:h-fit">
          <h2 className="text-xs uppercase tracking-wide text-crema/50 mb-3">Categorías</h2>

          {cargandoCategorias ? (
            <div className="flex flex-row gap-2 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible lg:pb-0 lg:space-y-2">
              {[1, 2, 3].map((n) => (
                <div key={n} className="h-8 w-24 shrink-0 lg:w-full rounded-lg bg-carbon-800 animate-pulseSoft" />
              ))}
            </div>
          ) : (
            <ul className="flex flex-row gap-2 overflow-x-auto pb-1 -mx-1 px-1 lg:flex-col lg:overflow-visible lg:pb-0 lg:mx-0 lg:px-0 lg:space-y-1">
              {categorias.map((cat) => (
                <li key={cat.id} className="shrink-0 lg:shrink">
                  <button
                    onClick={() => setCategoriaId(cat.id)}
                    className={`whitespace-nowrap lg:whitespace-normal lg:w-full text-left rounded-lg px-3 py-2 text-sm transition-colors duration-150 ${
                      cat.id === categoriaId
                        ? 'bg-carbon-800 text-dorado'
                        : 'text-crema/70 hover:bg-carbon-800 hover:text-crema'
                    }`}
                  >
                    {cat.nombre}
                  </button>
                </li>
              ))}
              {categorias.length === 0 && (
                <li className="text-sm text-crema/40 px-1 py-2">Sin categorías todavía.</li>
              )}
            </ul>
          )}

          {puedeEditar && (
            <form onSubmit={onCrearCategoria} className="mt-4 pt-4 border-t border-carbon-700 space-y-2">
              <input
                className="input-carbon text-sm"
                placeholder="Nueva categoría"
                value={nombreNuevaCategoria}
                onChange={(e) => setNombreNuevaCategoria(e.target.value)}
              />
              <button type="submit" disabled={creandoCategoria} className="btn-secundario w-full text-xs py-2">
                {creandoCategoria ? 'Creando…' : '+ Agregar categoría'}
              </button>
            </form>
          )}
        </div>

        {/* ── Ítems ──────────────────────────────────────────────── */}
        <div className="min-w-0">
          <div className="flex items-center justify-between gap-2 flex-wrap mb-3">
            <h2 className="text-sm text-crema/60">
              {categorias.find((c) => c.id === categoriaId)?.nombre || 'Selecciona una categoría'}
            </h2>
            {puedeEditar && categoriaId && (
              <button onClick={() => setModal({ modo: 'crear' })} className="btn-primario text-xs py-2">
                + Nuevo plato
              </button>
            )}
          </div>

          {cargandoItems ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
              {[1, 2, 3].map((n) => (
                <div key={n} className="h-40 rounded-2xl bg-carbon-800 animate-pulseSoft" />
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
              {items.map((item) => (
                <div key={item.id} className="card overflow-hidden animate-fadeInUp group">
                  <div className="aspect-[4/3] bg-carbon-800 overflow-hidden">
                    {item.imagenUrl ? (
                      <img
                        src={item.imagenUrl}
                        alt={item.nombre}
                        className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-crema/20 text-xs">
                        Sin foto
                      </div>
                    )}
                  </div>
                  <div className="p-4">
                    <div className="flex items-start justify-between gap-2">
                      <h3 className="font-medium text-crema text-sm">{item.nombre}</h3>
                      <span className="text-dorado text-sm font-medium whitespace-nowrap">
                        {formatoPrecio.format(item.precio)}
                      </span>
                    </div>
                    {item.descripcion && (
                      <p className="text-xs text-crema/40 mt-1 line-clamp-2">{item.descripcion}</p>
                    )}

                    <div className="mt-3 space-y-2">
                      <button
                        onClick={() => puedeEditar && onCambiarDisponibilidad(item)}
                        disabled={!puedeEditar}
                        className={`text-[11px] px-2 py-1 rounded-full border transition-colors duration-150 ${
                          item.disponible
                            ? 'border-green-800 text-green-300 bg-green-950/40'
                            : 'border-carbon-600 text-crema/40 bg-carbon-800'
                        } ${puedeEditar ? 'cursor-pointer hover:brightness-110' : 'cursor-default'}`}
                      >
                        {item.disponible ? 'Disponible' : 'Agotado'}
                      </button>

                      {puedeEditar && (
                        <div className="flex gap-2">
                          <button
                            onClick={() => setModal({ modo: 'editar', item })}
                            className="btn-secundario flex-1 text-xs py-2"
                          >
                            Editar
                          </button>
                          <button onClick={() => onEliminarItem(item)} className="btn-peligro flex-1 py-2">
                            Eliminar
                          </button>
                        </div>
                      )}
                      {puedeEditar && (
                        <button
                          onClick={() => setItemIngredientes(item)}
                          className="btn-secundario w-full text-xs py-2 mt-2"
                        >
                          Ingredientes
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              ))}

              {items.length === 0 && categoriaId && (
                <div className="col-span-full text-sm text-crema/40 py-10 text-center">
                  Esta categoría todavía no tiene platos.
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {itemIngredientes && (
        <EditorIngredientes
          item={itemIngredientes}
          onCerrar={() => setItemIngredientes(null)}
          onPresentacionCambiada={(actualizado) =>
            setItems((prev) => prev.map((i) => (i.id === actualizado.id ? { ...i, ...actualizado } : i)))
          }
        />
      )}

      {modal && (
        <ModalItem
          modo={modal.modo}
          item={modal.item}
          onCancelar={() => setModal(null)}
          onGuardar={onGuardarItem}
        />
      )}
    </div>
  )
}

function ModalItem({ modo, item, onCancelar, onGuardar }) {
  const [nombre, setNombre] = useState(item?.nombre || '')
  const [descripcion, setDescripcion] = useState(item?.descripcion || '')
  const [precio, setPrecio] = useState(item?.precio ?? '')
  const [archivo, setArchivo] = useState(null)
  const [guardando, setGuardando] = useState(false)

  async function onSubmit(e) {
    e.preventDefault()
    setGuardando(true)
    await onGuardar({
      nombre,
      descripcion: descripcion || null,
      precio: Number(precio),
      archivo,
      itemExistente: modo === 'editar' ? item : null,
    })
    setGuardando(false)
  }

  return (
    <div className="fixed inset-0 bg-carbon-950/80 backdrop-blur-sm flex items-center justify-center px-4 py-6 z-50 animate-fadeInUp">
      {/* max-h-[90dvh] + overflow-y-auto: en un celular con el teclado
          abierto la altura visible baja muchísimo (iPhone SE ~= 400px
          libres) y este formulario tiene 4 campos + 2 botones -- sin esto
          el botón "Guardar" queda fuera de pantalla e inalcanzable. */}
      <div className="card w-full max-w-md p-6 max-h-[90dvh] overflow-y-auto">
        <h2 className="font-display font-semibold text-xl text-crema mb-4">
          {modo === 'crear' ? 'Nuevo plato' : 'Editar plato'}
        </h2>

        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label className="label-carbon">Nombre</label>
            <input className="input-carbon" value={nombre} onChange={(e) => setNombre(e.target.value)} required />
          </div>
          <div>
            <label className="label-carbon">Descripción</label>
            <textarea
              className="input-carbon h-auto py-2"
              rows={2}
              value={descripcion}
              onChange={(e) => setDescripcion(e.target.value)}
            />
          </div>
          <div>
            <label className="label-carbon">Precio (COP)</label>
            <input
              type="number" min="0" step="1"
              className="input-carbon"
              value={precio}
              onChange={(e) => setPrecio(e.target.value)}
              required
            />
          </div>
          <div>
            <label className="label-carbon">Foto (JPEG, PNG o WEBP, máx. 5MB)</label>
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={(e) => setArchivo(e.target.files[0] || null)}
              className="text-xs text-crema/60"
            />
          </div>

          <div className="flex gap-2 pt-2">
            <button type="button" onClick={onCancelar} className="btn-secundario flex-1">
              Cancelar
            </button>
            <button type="submit" disabled={guardando} className="btn-primario flex-1">
              {guardando ? 'Guardando…' : 'Guardar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
