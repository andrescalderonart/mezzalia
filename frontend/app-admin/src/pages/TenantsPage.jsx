import { useEffect, useState } from 'react'
import { useApiJson } from '../context/AuthContext.jsx'

const ESTADO_ACTIVO = 'ACTIVO'
const ESTADO_SUSPENDIDO = 'SUSPENDIDO'

const formularioVacio = { nombre: '', plan: '', moneda: 'COP', zonaHoraria: 'America/Bogota' }

export default function TenantsPage() {
  const apiJson = useApiJson()

  const [tenants, setTenants] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  const [formulario, setFormulario] = useState(formularioVacio)
  const [creando, setCreando] = useState(false)
  const [mostrarFormulario, setMostrarFormulario] = useState(false)

  // idTenant en el que hay una accion de suspender/reactivar en curso --
  // solo uno a la vez, para deshabilitar exclusivamente ese boton (mismo
  // criterio que generandoId en MesasPage).
  const [accionandoId, setAccionandoId] = useState(null)

  async function cargarTenants() {
    setCargando(true)
    setError('')
    try {
      const datos = await apiJson('/api/admin/tenants')
      setTenants(datos)
    } catch (e) {
      setError(e.message)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    cargarTenants()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function onCrearTenant(e) {
    e.preventDefault()
    if (!formulario.nombre.trim() || !formulario.plan.trim() || !formulario.moneda.trim() || !formulario.zonaHoraria.trim()) {
      return
    }
    setCreando(true)
    setError('')
    try {
      const tenant = await apiJson('/api/admin/tenants', {
        method: 'POST',
        body: {
          nombre: formulario.nombre.trim(),
          plan: formulario.plan.trim(),
          moneda: formulario.moneda.trim(),
          zonaHoraria: formulario.zonaHoraria.trim(),
        },
      })
      // La lista la devuelve el backend ordenada por nombre -- se recarga
      // completa en vez de insertar a mano en el estado local para no
      // duplicar ese criterio de orden acá.
      await cargarTenants()
      setFormulario(formularioVacio)
      setMostrarFormulario(false)
      void tenant
    } catch (e) {
      setError(e.message)
    } finally {
      setCreando(false)
    }
  }

  async function onCambiarEstado(tenant, accion) {
    const confirmacion =
      accion === 'suspender'
        ? `¿Suspender "${tenant.nombre}"? Deja de servir su carta y branding públicos de inmediato.`
        : `¿Reactivar "${tenant.nombre}"?`
    if (!window.confirm(confirmacion)) return

    setAccionandoId(tenant.id)
    setError('')
    try {
      await apiJson(`/api/admin/tenants/${tenant.id}/${accion}`, { method: 'PATCH' })
      setTenants((prev) =>
        prev.map((t) =>
          t.id === tenant.id ? { ...t, estado: accion === 'suspender' ? ESTADO_SUSPENDIDO : ESTADO_ACTIVO } : t,
        ),
      )
    } catch (e) {
      setError(e.message)
    } finally {
      setAccionandoId(null)
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6 gap-3">
        <div>
          <h1 className="font-display text-2xl text-crema">Restaurantes</h1>
          <p className="text-sm text-crema/40">
            Alta y suspensión de tenants. El equipo Mezzalia administra esto, no cada restaurante.
          </p>
        </div>
        <button onClick={() => setMostrarFormulario((v) => !v)} className="btn-primario shrink-0">
          {mostrarFormulario ? 'Cancelar' : '+ Nuevo restaurante'}
        </button>
      </div>

      {error && (
        <div className="mb-4 text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2 animate-fadeInUp">
          {error}
        </div>
      )}

      {mostrarFormulario && (
        <form onSubmit={onCrearTenant} className="card p-4 mb-6 grid grid-cols-1 sm:grid-cols-2 gap-3 animate-fadeInUp">
          <input
            className="input-carbon"
            placeholder="Nombre del restaurante"
            value={formulario.nombre}
            onChange={(e) => setFormulario((f) => ({ ...f, nombre: e.target.value }))}
          />
          <input
            className="input-carbon"
            placeholder="Plan (ej. STARTER)"
            value={formulario.plan}
            onChange={(e) => setFormulario((f) => ({ ...f, plan: e.target.value }))}
          />
          <input
            className="input-carbon"
            placeholder="Moneda (ej. COP)"
            value={formulario.moneda}
            onChange={(e) => setFormulario((f) => ({ ...f, moneda: e.target.value }))}
          />
          <input
            className="input-carbon"
            placeholder="Zona horaria (ej. America/Bogota)"
            value={formulario.zonaHoraria}
            onChange={(e) => setFormulario((f) => ({ ...f, zonaHoraria: e.target.value }))}
          />
          <div className="sm:col-span-2">
            <button type="submit" disabled={creando} className="btn-primario w-full sm:w-auto">
              {creando ? 'Creando…' : 'Crear restaurante'}
            </button>
            <p className="text-[11px] text-crema/40 mt-2">
              Esto solo da de alta el tenant. Crear el primer usuario admin_restaurante para que puedan entrar
              todavía es un paso manual aparte (no hay CRUD de usuarios todavía).
            </p>
          </div>
        </form>
      )}

      {cargando ? (
        <div className="space-y-3">
          {[1, 2, 3].map((n) => (
            <div key={n} className="h-16 rounded-2xl bg-carbon-800 animate-pulseSoft" />
          ))}
        </div>
      ) : (
        <div className="space-y-3">
          {tenants.map((tenant) => {
            const suspendido = tenant.estado === ESTADO_SUSPENDIDO
            return (
              <div key={tenant.id} className="card p-4 flex flex-col sm:flex-row sm:items-center gap-3 animate-fadeInUp">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="font-medium text-crema truncate">{tenant.nombre}</h3>
                    <span
                      className={`text-[10px] px-2 py-0.5 rounded-full border ${
                        suspendido
                          ? 'text-red-300 border-red-900/60 bg-red-950/40'
                          : 'text-emerald-300 border-emerald-900/60 bg-emerald-950/40'
                      }`}
                    >
                      {tenant.estado}
                    </span>
                  </div>
                  <p className="text-[11px] text-crema/40 mt-1">
                    {tenant.plan} · {tenant.moneda} · {tenant.zonaHoraria}
                  </p>
                </div>
                <button
                  onClick={() => onCambiarEstado(tenant, suspendido ? 'reactivar' : 'suspender')}
                  disabled={accionandoId === tenant.id}
                  className="btn-secundario shrink-0 text-xs py-2 px-4"
                >
                  {accionandoId === tenant.id ? '…' : suspendido ? 'Reactivar' : 'Suspender'}
                </button>
              </div>
            )
          })}

          {tenants.length === 0 && (
            <div className="text-sm text-crema/40 py-10 text-center">Todavía no hay restaurantes dados de alta.</div>
          )}
        </div>
      )}
    </div>
  )
}
