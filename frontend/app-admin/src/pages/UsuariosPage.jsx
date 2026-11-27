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

  // RF-10 PR4: sucursales activas del tenant, para poder armar el
  // selector de "a qué sucursales puede entrar este MESERO" -- ver
  // GestionSucursalesModal más abajo. Se carga una sola vez acá (no por
  // fila) porque es la misma lista para todos los usuarios del tenant.
  const [sucursales, setSucursales] = useState([])
  const [gestionandoUsuario, setGestionandoUsuario] = useState(null)

  // Pedido de Andrés (2026-09-07): ver horas trabajadas por día -- ver
  // HorasPorDiaModal más abajo y turno.servicio.TurnoService en el backend.
  const [viendoHorasDe, setViendoHorasDe] = useState(null)

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

  useEffect(() => {
    cargarUsuarios()
    apiJson('/api/admin/sucursales')
      .then(setSucursales)
      .catch(() => {
        // Si esto falla, el botón "Sucursales" de cada MESERO simplemente
        // no va a tener nada para mostrar en el modal (ver el mensaje de
        // "no hay sucursales" ahí) -- no vale la pena bloquear toda la
        // pantalla de Usuarios por esto.
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

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

  return (
    <div>
      <div className="mb-6">
        <h1 className="font-display font-semibold text-2xl text-crema">Usuarios</h1>
        <p className="text-sm text-crema/55">
          Da de alta o desactiva cuentas del personal de este restaurante. No hay recuperación de
          contraseña por email todavía -- si alguien la olvida, reseteala vos desde acá.
        </p>
      </div>

      {error && (
        <div className="mb-4 text-sm text-peligro bg-peligro/10 border border-peligro/25 rounded-lg px-3 py-2 animate-fadeInUp">
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
                <div className="text-[11px] text-crema/55">
                  {usuario.rol} · {usuario.activo ? 'activo' : 'inactivo'}
                </div>
              </div>
              <div className="flex gap-2">
                {/* RF-10 PR4: un MESERO solo puede trabajar (elegir sucursal
                    al loguearse, ver SeleccionarSucursalPage.jsx) en las
                    sucursales que un ADMIN_RESTAURANTE le asigne acá --
                    ADMIN_RESTAURANTE nunca necesita esto, ya ve todas las
                    de su tenant sin restricción (ver
                    AsignacionSucursalExceptions.RolNoPermitidoParaAsignacionException
                    en el backend: asignarle una a un admin ni siquiera está
                    permitido). */}
                {usuario.rol === 'MESERO' && (
                  <button onClick={() => setGestionandoUsuario(usuario)} className="btn-secundario text-xs py-2">
                    Sucursales
                  </button>
                )}
                <button onClick={() => setViendoHorasDe(usuario)} className="btn-secundario text-xs py-2">
                  Horas
                </button>
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
                      ? 'text-crema/55 hover:text-peligro'
                      : 'text-crema/55 hover:text-exito'
                  }`}
                >
                  {usuario.activo ? 'Desactivar' : 'Reactivar'}
                </button>
              </div>
            </div>
          ))}

          {usuarios.length === 0 && (
            <div className="text-sm text-crema/55 py-10 text-center">Todavía no hay usuarios creados.</div>
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

      {gestionandoUsuario && (
        <GestionSucursalesModal
          usuario={gestionandoUsuario}
          sucursales={sucursales}
          onCerrar={() => setGestionandoUsuario(null)}
        />
      )}

      {viendoHorasDe && (
        <HorasPorDiaModal usuario={viendoHorasDe} onCerrar={() => setViendoHorasDe(null)} />
      )}
    </div>
  )
}

/**
 * RF-10 PR4: modal para asignar/desasignar a un MESERO las sucursales en
 * las que puede elegir trabajar (ver SeleccionarSucursalPage.jsx del lado
 * del propio mesero, y AsignacionSucursalService en el backend). Sin
 * ninguna asignación, un MESERO ve la pantalla de selección de sucursal
 * completamente vacía y no puede entrar a operar -- por eso el aviso
 * cuando la lista de asignadas queda en cero.
 *
 * Carga sus asignaciones actuales al abrir (GET /api/admin/usuarios/{id}/sucursales)
 * en vez de mantenerlas precargadas por cada fila de la tabla, para no
 * disparar un GET extra por cada MESERO listado solo por si acaso se abre
 * su modal.
 *
 * FIX 2026-09-10 (backlog del proyecto, punto 1): las opciones del modal
 * ya NO son solo `sucursales` (la prop, activas del tenant) -- se le suman
 * las sucursales que este usuario ya tiene asignadas aunque estén
 * inactivas. Antes de este fix, si un MESERO quedaba asignado a una
 * sucursal que después se desactivaba, la asignación seguía viva en la
 * base (AsignacionSucursal no se toca al desactivar, a propósito) pero
 * desaparecía por completo de este modal: no había fila ni checkbox para
 * verla ni para desasignarla. GET /api/admin/usuarios/{id}/sucursales ya
 * devuelve esas sucursales inactivas (ver el javadoc de
 * SucursalRepository.findByTenantIdAndIdInOrderByNombreAsc en el
 * backend) -- lo único que faltaba era usarlas para render, no solo para
 * marcar el checkbox. Una sucursal inactiva se muestra atenuada, con
 * "(inactiva)", y su checkbox solo permite QUITAR (DELETE), nunca volver
 * a asignarla (PUT) -- mismo criterio que ya usa
 * abrirAsignacionSucursales en este mismo archivo para el histórico
 * previo de esta pantalla.
 */
function GestionSucursalesModal({ usuario, sucursales, onCerrar }) {
  const apiJson = useApiJson()
  const [asignadas, setAsignadas] = useState(null) // objetos completos (id + nombre + activa), no solo ids
  const [error, setError] = useState('')
  const [actualizandoId, setActualizandoId] = useState(null)

  useEffect(() => {
    let vigente = true
    apiJson(`/api/admin/usuarios/${usuario.id}/sucursales`)
      .then((datos) => {
        if (vigente) setAsignadas(datos)
      })
      .catch((e) => {
        if (vigente) setError(e.message)
      })
    return () => {
      vigente = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario.id])

  const asignadasIds = asignadas === null ? [] : asignadas.map((s) => s.id)

  async function onToggle(sucursal) {
    const yaAsignada = asignadasIds.includes(sucursal.id)
    setActualizandoId(sucursal.id)
    setError('')
    try {
      await apiJson(`/api/admin/usuarios/${usuario.id}/sucursales/${sucursal.id}`, {
        method: yaAsignada ? 'DELETE' : 'PUT',
      })
      setAsignadas((prev) => (yaAsignada ? prev.filter((s) => s.id !== sucursal.id) : [...prev, sucursal]))
    } catch (e) {
      setError(e.message)
    } finally {
      setActualizandoId(null)
    }
  }

  const cargando = asignadas === null

  // Union de "activas del tenant" + "asignadas a este usuario aunque
  // estén inactivas" -- ver el comentario largo arriba del componente.
  const opciones =
    asignadas === null
      ? sucursales
      : [...sucursales, ...asignadas.filter((s) => !sucursales.some((activa) => activa.id === s.id))].sort(
          (a, b) => a.nombre.localeCompare(b.nombre),
        )

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={onCerrar}>
      <div onClick={(e) => e.stopPropagation()} className="card p-5 w-full max-w-sm flex flex-col gap-3">
        <div>
          <h3 className="font-medium text-crema">Sucursales de {usuario.username}</h3>
          <p className="text-[11px] text-crema/55 mt-1">
            Solo puede elegir trabajar en las sucursales que marques acá. Sin ninguna, no va a poder
            entrar a Pedidos.
          </p>
        </div>

        {error && (
          <div className="text-sm text-peligro bg-peligro/10 border border-peligro/25 rounded-lg px-3 py-2 animate-fadeInUp">
            {error}
          </div>
        )}

        {cargando ? (
          <div className="space-y-2">
            {[1, 2].map((n) => (
              <div key={n} className="h-10 rounded-lg bg-carbon-800 animate-pulseSoft" />
            ))}
          </div>
        ) : opciones.length === 0 ? (
          <p className="text-sm text-crema/55 py-4 text-center">
            Este restaurante todavía no tiene sucursales creadas -- creá una primero en
            "Sucursales".
          </p>
        ) : (
          <div className="space-y-1.5 max-h-72 overflow-y-auto">
            {opciones.map((sucursal) => {
              const asignada = asignadasIds.includes(sucursal.id)
              const esInactivaNoReasignable = !sucursal.activa
              return (
                <label
                  key={sucursal.id}
                  className={`flex items-center justify-between gap-3 rounded-lg px-3 py-2 bg-carbon-800/60 cursor-pointer ${
                    esInactivaNoReasignable ? 'opacity-60' : ''
                  }`}
                >
                  <span className="text-sm text-crema">
                    {sucursal.nombre}
                    {esInactivaNoReasignable && <span className="text-[10px] text-crema/55"> (inactiva)</span>}
                  </span>
                  <input
                    type="checkbox"
                    checked={asignada}
                    disabled={actualizandoId === sucursal.id || (esInactivaNoReasignable && !asignada)}
                    onChange={() => onToggle(sucursal)}
                    className="w-4 h-4 accent-dorado"
                  />
                </label>
              )
            })}
          </div>
        )}

        {!cargando && opciones.length > 0 && asignadasIds.length === 0 && (
          <p className="text-xs text-dorado/80 bg-dorado/10 border border-dorado/20 rounded-lg px-2.5 py-1.5">
            Todavía no tiene ninguna sucursal asignada.
          </p>
        )}

        <button onClick={onCerrar} className="btn-secundario w-full text-xs py-2">
          Cerrar
        </button>
      </div>
    </div>
  )
}

/**
 * Pedido de Andrés (2026-09-07): ver cuánto tiempo trabajado por día lleva
 * cada usuario. Backend: GET /api/admin/usuarios/{id}/turnos (nuevo --
 * ver UsuarioInternoAdminController.horasPorDia/turno.servicio.TurnoService).
 *
 * El número de "hoy" puede seguir subiendo mientras la persona siga en
 * turno (ver el badge "en curso") -- a diferencia de los demás días, que
 * ya son totales definitivos. El dato depende 100% de que la persona haya
 * usado "Marcar entrada/Marcar salida" (ver TurnoWidget en el sidebar) --
 * si nunca lo usó, esta lista sale vacía, no es un bug.
 */
function HorasPorDiaModal({ usuario, onCerrar }) {
  const apiJson = useApiJson()
  const [dias, setDias] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let vigente = true
    apiJson(`/api/admin/usuarios/${usuario.id}/turnos?dias=14`)
      .then((datos) => {
        if (vigente) setDias(datos)
      })
      .catch((e) => {
        if (vigente) setError(e.message)
      })
    return () => {
      vigente = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario.id])

  function formatearFecha(fechaIso) {
    const [anio, mes, dia] = fechaIso.split('-').map(Number)
    return new Date(anio, mes - 1, dia).toLocaleDateString('es-CO', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
    })
  }

  function formatearHoras(horas) {
    const horasEnteras = Math.floor(horas)
    const minutos = Math.round((horas - horasEnteras) * 60)
    return `${horasEnteras}h ${minutos}min`
  }

  const cargando = dias === null
  const diasOrdenados = cargando ? [] : [...dias].sort((a, b) => (a.fecha < b.fecha ? 1 : -1))

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={onCerrar}>
      <div onClick={(e) => e.stopPropagation()} className="card p-5 w-full max-w-sm flex flex-col gap-3">
        <div>
          <h3 className="font-medium text-crema">Horas de {usuario.username}</h3>
          <p className="text-[11px] text-crema/55 mt-1">Últimos 14 días, según sus marcas de entrada/salida.</p>
        </div>

        {error && (
          <div className="text-sm text-peligro bg-peligro/10 border border-peligro/25 rounded-lg px-3 py-2 animate-fadeInUp">
            {error}
          </div>
        )}

        {cargando ? (
          <div className="space-y-2">
            {[1, 2, 3].map((n) => (
              <div key={n} className="h-9 rounded-lg bg-carbon-800 animate-pulseSoft" />
            ))}
          </div>
        ) : diasOrdenados.length === 0 ? (
          <p className="text-sm text-crema/55 py-4 text-center">
            Todavía no marcó ninguna entrada en los últimos 14 días.
          </p>
        ) : (
          <div className="space-y-1 max-h-72 overflow-y-auto">
            {diasOrdenados.map((d) => (
              <div key={d.fecha} className="flex items-center justify-between gap-3 rounded-lg px-3 py-2 bg-carbon-800/60">
                <span className="text-sm text-crema capitalize">{formatearFecha(d.fecha)}</span>
                <span className="text-sm text-crema/70">
                  {formatearHoras(d.horas)}
                  {d.turnoAbiertoIncluido && <span className="text-dorado ml-1">· en curso</span>}
                </span>
              </div>
            ))}
          </div>
        )}

        <button onClick={onCerrar} className="btn-secundario w-full text-xs py-2">
          Cerrar
        </button>
      </div>
    </div>
  )
}
