import { useEffect, useState } from 'react'
import { useApiJson } from '../context/AuthContext.jsx'

// RF-10/documentacion-tecnica.md §7 -- exclusivo de ADMIN_RESTAURANTE (ver
// App.jsx/Layout.jsx). Backend: sucursal.controlador.SucursalAdminController.
// ALCANCE REAL (ver el aviso largo en backend/.../sucursal/Sucursal.java):
// hoy solo Mesa queda asociada a una sucursal -- la carta y los pedidos
// todavía se comparten entre todas las sucursales de un tenant.
export default function SucursalesPage() {
  const apiJson = useApiJson()

  const [sucursales, setSucursales] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  const [nombre, setNombre] = useState('')
  const [direccion, setDireccion] = useState('')
  const [creando, setCreando] = useState(false)

  async function cargarSucursales() {
    setCargando(true)
    setError('')
    try {
      setSucursales(await apiJson('/api/admin/sucursales'))
    } catch (e) {
      setError(e.message)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    cargarSucursales()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function onCrearSucursal(e) {
    e.preventDefault()
    if (!nombre.trim()) return
    setCreando(true)
    setError('')
    try {
      const sucursal = await apiJson('/api/admin/sucursales', {
        method: 'POST',
        body: { nombre: nombre.trim(), direccion: direccion.trim() || null },
      })
      setSucursales((prev) => [...prev, sucursal].sort((a, b) => a.nombre.localeCompare(b.nombre)))
      setNombre('')
      setDireccion('')
    } catch (e) {
      setError(e.message)
    } finally {
      setCreando(false)
    }
  }

  async function onDesactivar(sucursal) {
    if (!window.confirm(`¿Desactivar "${sucursal.nombre}"? Las mesas que ya tenga siguen existiendo, pero no vas a poder crear mesas nuevas ahí.`)) {
      return
    }
    setError('')
    try {
      await apiJson(`/api/admin/sucursales/${sucursal.id}/desactivar`, { method: 'PATCH' })
      setSucursales((prev) => prev.map((s) => (s.id === sucursal.id ? { ...s, activa: false } : s)))
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="font-display text-2xl text-crema">Sucursales</h1>
        <p className="text-sm text-crema/40">
          La mayoría de restaurantes opera con una sola sucursal. Agregá más solo si de verdad
          tenés más de un local.
        </p>
      </div>

      {error && (
        <div className="mb-4 text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2 animate-fadeInUp">
          {error}
        </div>
      )}

      <form onSubmit={onCrearSucursal} className="card p-4 mb-6 flex flex-col sm:flex-row gap-2">
        <input
          className="input-carbon flex-1"
          placeholder="Nombre (ej. Sede Chapinero)"
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
        />
        <input
          className="input-carbon flex-1"
          placeholder="Dirección (opcional)"
          value={direccion}
          onChange={(e) => setDireccion(e.target.value)}
        />
        <button type="submit" disabled={creando} className="btn-primario sm:w-auto">
          {creando ? 'Creando…' : '+ Nueva sucursal'}
        </button>
      </form>

      {cargando ? (
        <div className="space-y-2">
          {[1, 2].map((n) => (
            <div key={n} className="h-14 rounded-xl bg-carbon-800 animate-pulseSoft" />
          ))}
        </div>
      ) : (
        <div className="card divide-y divide-carbon-700">
          {sucursales.map((sucursal) => (
            <div key={sucursal.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <div className="text-sm text-crema">{sucursal.nombre}</div>
                <div className="text-[11px] text-crema/40">{sucursal.direccion || 'Sin dirección registrada'}</div>
              </div>
              {sucursal.activa && (
                <button onClick={() => onDesactivar(sucursal)} className="text-xs py-2 px-3 rounded-lg text-crema/40 hover:text-red-300 transition-colors duration-150">
                  Desactivar
                </button>
              )}
            </div>
          ))}

          {sucursales.length === 0 && (
            <div className="text-sm text-crema/40 py-10 text-center">Todavía no hay sucursales creadas.</div>
          )}
        </div>
      )}
    </div>
  )
}
