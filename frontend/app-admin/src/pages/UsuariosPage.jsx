import { useEffect, useState } from 'react'
import { useApiJson } from '../context/AuthContext.jsx'

// RF-09/documentacion-tecnica.md §2: "gestión de usuarios internos con
// roles" -- exclusivo de ADMIN_RESTAURANTE (ver App.jsx: esta pantalla
// vive bajo su propio RutaConRol, ni siquiera MESERO la ve en el nav, ver
// Layout.jsx). Backend: identidad.controlador.UsuarioInternoAdminController.
const ROLES_ASIGNABLES = ['ADMIN_RESTAURANTE', 'MESERO']

export default function UsuariosPage() {
  const apiJson = useApiJson()

  const [usuarios, setUsuarios] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [rol, setRol] = useState('MESERO')
  const [creando, setCreando] = useState(false)

  const [resetId, setResetId] = useState(null)
  const [passwordNueva, setPasswordNueva] = useState('')
  const [reseteando, setReseteando] = useState(false)

  // RF-10 PR4: sucursales ACTIVAS del tenant, para el modal de asignación
  // de abajo -- mismo endpoint que SucursalesPage/MesasPage.
  const [sucursales, setSucursales] = useState([])
  const [asignacionUsuario, setAsignacionUsuario] = useState(null)
  const [sucursalesAsignadas, setSucursalesAsignadas] = useState([])
  const [cargandoAsignaciones, setCargandoAsignaciones] = useState(false)
  const [errorAsignaciones, setErrorAsignaciones] = useState('')
  const [guardandoSucursalId, setGuardandoSucursalId] = useState(null)

  async function cargarUsuarios() {
    setCargando(true)
    setError('')
    try {
      setUsuarios(await apiJson('/api/admin/usuarios'))
    } catch (e) {
      setError(e.message)
    } finally {
      setCargando(false)
    }
  }

  async function cargarSucursales() {
    try {
      setSucursales(await apiJson('/api/admin/sucursales'))
    } catch {
      // No bloquea la pantalla de usuarios si esto falla -- el modal de
      // asignación de sucursales simplemente queda vacío hasta refrescar
      // (ver la lista de opciones abajo, que igual sigue mostrando las
      // sucursales ya asignadas que llegan directo de cada usuario).
    }
  }

  useEffect(() => {
    cargarUsuarios()
    cargarSucursales()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /**
   * RF-10 PR4: abre el modal de sucursales asignadas para un MESERO
   * puntual -- ver UsuarioInternoAdminController.listarSucursalesAsignadas.
   * A propósito NO filtra por activa (a diferencia de `sucursales` de
   * arriba): un MESERO puede seguir teniendo asignada una sucursal que
   * mientras tanto se desactivó (ver SucursalService.desactivarSucursal,
   * que no toca asignaciones existentes) y hay que poder verla/quitarla
   * igual, aunque ya no se pueda re-asignar (ver `opciones` más abajo).
   */
  async function abrirAsignacionSucursales(usuario) {
    setAsignacionUsuario(usuario)
    setErrorAsignaciones('')
    setCargandoAsignaciones(true)
    try {
      setSucursalesAsignadas(await apiJson(`/api/admin/usuarios/${usuario.id}/sucursales`))
    } catch (e) {
      setErrorAsignaciones(e.message)
    } finally {
      setCargandoAsignaciones(false)
    }
  }

  async function onToggleSucursal(sucursal, estaAsignada) {
    if (!asignacionUsuario) return
    setGuardandoSucursalId(sucursal.id)
    setErrorAsignaciones('')
    try {
      await apiJson(`/api/admin/usuarios/${asignacionUsuario.id}/sucursales/${sucursal.id}`, {
        method: estaAsignada ? 'DELETE' : 'PUT',
      })
      setSucursalesAsignadas((prev) =>
        estaAsignada
          ? prev.filter((s) => s.id !== sucursal.id)
          : [...prev, sucursal].sort((a, b) => a.nombre.localeCompare(b.nombre)),
      )
    } catch (e) {
      setErrorAsignaciones(e.message)
    } finally {
      setGuardandoSucursalId(null)
    }
  }

  async function onCrearUsuario(e) {
    e.preventDefault()
    if (!username.trim() || password.length < 8) return
    setCreando(true)
    setError('')
    try {
      const usuario = await apiJson('/api/admin/usuarios', {
        method: 'POST',
        body: { username: username.trim(), password, rol },
      })
      setUsuarios((prev) => [...prev, usuario].sort((a, b) => a.username.localeCompare(b.username)))
      setUsername('')
      setPassword('')
      setRol('MESERO')
    } catch (e) {
      setError(e.message)
    } finally {
      setCreando(false)
    }
  }

  async function onCambiarActivo(usuario) {
    const accion = usuario.activo ? 'desactivar' : 'reactivar'
    if (usuario.activo && !window.confirm(`¿Desactivar a "${usuario.username}"? No va a poder iniciar sesión hasta que lo reactives.`)) {
      return
    }
    setError('')
    try {
      await apiJson(`/api/admin/usuarios/${usuario.id}/${accion}`, { method: 'PATCH' })
      setUsuarios((prev) => prev.map((u) => (u.id === usuario.id ? { ...u, activo: !u.activo } : u)))
    } catch (e) {
      setError(e.message)
    }
  }

  async function onResetearPassword(e) {
    e.preventDefault()
    if (!resetId || passwordNueva.length < 8) return
    setReseteando(true)
    setError('')
    try {
      await apiJson(`/api/admin/usuarios/${resetId}/password`, {
        method: 'PATCH',
        body: { passwordNueva },
      })
      setResetId(null)
      setPasswordNueva('')
    } catch (e) {
      setError(e.message)
    } finally {
      setReseteando(false)
    }
  }

  // RF-10 PR4: opciones del modal = sucursales activas del tenant + las ya
  // asignadas que quedaron inactivas mientras tanto (ver el javadoc de
  // abrirAsignacionSucursales) -- sin este merge, una asignación a una
  // sucursal desactivada desaparecería de la vista sin poder quitarla
  // nunca desde acá.
  const idsAsignados = new Set(sucursalesAsignadas.map((s) => s.id))
  const opcionesAsignacion = [
    ...sucursales,
    ...sucursalesAsignadas.filter((s) => !sucursales.some((activa) => activa.id === s.id)),
  ].sort((a, b) => a.nombre.localeCompare(b.nombre))

  return (
    <div>
      <div className="mb-6">
        <h1 className="font-display text-2xl text-crema">Usuarios</h1>
        <p className="text-sm text-crema/40">
          Da de alta o desactiva cuentas del personal de este restaurante. No hay recuperación de
          contraseña por email todavía -- si alguien la olvida, reseteala vos desde acá.
        </p>
      </div>

      {error && (
        <div className="mb-4 text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2 animate-fadeInUp">
          {error}
        </div>
      )}

      <form onSubmit={onCrearUsuario} className="card p-4 mb-6 flex flex-col sm:flex-row gap-2">
        <input
          className="input-carbon flex-1"
          placeholder="Username (ej. mesero.ana)"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
        <input
          className="input-carbon flex-1"
          placeholder="Contraseña inicial (mín. 8 caracteres)"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <select className="input-carbon sm:w-48" value={rol} onChange={(e) => setRol(e.target.value)}>
          {ROLES_ASIGNABLES.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </select>
        <button type="submit" disabled={creando} className="btn-primario sm:w-auto">
          {creando ? 'Creando…' : '+ Nuevo usuario'}
        </button>
      </form>

      {cargando ? (
        <div className="space-y-2">
          {[1, 2, 3].map((n) => (
            <div key={n} className="h-14 rounded-xl bg-carbon-800 animate-pulseSoft" />
          ))}
        </div>
      ) : (
        <div className="card divide-y divide-carbon-700">
          {usuarios.map((usuario) => (
            <div key={usuario.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <div className="text-sm text-crema">{usuario.username}</div>
                <div className="text-[11px] text-crema/40">
                  {usuario.rol} · {usuario.activo ? 'activo' : 'inactivo'}
                </div>
              </div>
              <div className="flex gap-2">
                {usuario.rol === 'MESERO' && (
                  <button
                    onClick={() => abrirAsignacionSucursales(usuario)}
                    className="btn-secundario text-xs py-2"
                  >
                    Sucursales
                  </button>
                )}
                <button
                  onClick={() => {
                    setResetId(usuario.id)
                    setPasswordNueva('')
                  }}
                  className="btn-secundario text-xs py-2"
                >
                  Resetear contraseña
                </button>
                <button
                  onClick={() => onCambiarActivo(usuario)}
                  className={`text-xs py-2 px-3 rounded-lg transition-colors duration-150 ${
                    usuario.activo
                      ? 'text-crema/40 hover:text-red-300'
                      : 'text-crema/40 hover:text-green-300'
                  }`}
                >
                  {usuario.activo ? 'Desactivar' : 'Reactivar'}
                </button>
              </div>
            </div>
          ))}

          {usuarios.length === 0 && (
            <div className="text-sm text-crema/40 py-10 text-center">Todavía no hay usuarios creados.</div>
          )}
        </div>
      )}

      {resetId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={() => setResetId(null)}>
          <form
            onSubmit={onResetearPassword}
            onClick={(e) => e.stopPropagation()}
            className="card p-5 w-full max-w-sm flex flex-col gap-3"
          >
            <h3 className="font-medium text-crema">Nueva contraseña</h3>
            <input
              className="input-carbon"
              placeholder="Mínimo 8 caracteres"
              type="password"
              autoFocus
              value={passwordNueva}
              onChange={(e) => setPasswordNueva(e.target.value)}
            />
            <div className="flex gap-2">
              <button type="button" onClick={() => setResetId(null)} className="btn-secundario flex-1 text-xs py-2">
                Cancelar
              </button>
              <button type="submit" disabled={reseteando} className="btn-primario flex-1 text-xs py-2">
                {reseteando ? 'Guardando…' : 'Guardar'}
              </button>
            </div>
          </form>
        </div>
      )}

      {asignacionUsuario && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
          onClick={() => setAsignacionUsuario(null)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="card p-5 w-full max-w-sm flex flex-col gap-3 max-h-[80vh] overflow-y-auto"
          >
            <div>
              <h3 className="font-medium text-crema">Sucursales de {asignacionUsuario.username}</h3>
              <p className="text-[11px] text-crema/40 mt-1">
                Solo puede trabajar en las que tenga marcadas acá. Al iniciar sesión va a elegir una de
                estas.
              </p>
            </div>

            {errorAsignaciones && (
              <div className="text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2">
                {errorAsignaciones}
              </div>
            )}

            {cargandoAsignaciones ? (
              <div className="space-y-2">
                {[1, 2].map((n) => (
                  <div key={n} className="h-10 rounded-lg bg-carbon-800 animate-pulseSoft" />
                ))}
              </div>
            ) : opcionesAsignacion.length === 0 ? (
              <p className="text-sm text-crema/40 text-center py-4">
                Todavía no hay sucursales creadas -- creá una primero en Sucursales.
              </p>
            ) : (
              <div className="space-y-1">
                {opcionesAsignacion.map((sucursal) => {
                  const estaAsignada = idsAsignados.has(sucursal.id)
                  const esInactivaNoReasignable = !sucursal.activa
                  return (
                    <label
                      key={sucursal.id}
                      className={`flex items-center justify-between gap-2 px-3 py-2 rounded-lg cursor-pointer ${
                        esInactivaNoReasignable ? 'opacity-50' : 'hover:bg-carbon-800'
                      }`}
                    >
                      <span className="text-sm text-crema">
                        {sucursal.nombre}
                        {esInactivaNoReasignable && (
                          <span className="text-[10px] text-crema/40"> (inactiva)</span>
                        )}
                      </span>
                      <input
                        type="checkbox"
                        checked={estaAsignada}
                        disabled={guardandoSucursalId === sucursal.id || (esInactivaNoReasignable && !estaAsignada)}
                        onChange={() => onToggleSucursal(sucursal, estaAsignada)}
                      />
                    </label>
                  )
                })}
              </div>
            )}

            <button onClick={() => setAsignacionUsuario(null)} className="btn-secundario text-xs py-2 mt-2">
              Cerrar
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
