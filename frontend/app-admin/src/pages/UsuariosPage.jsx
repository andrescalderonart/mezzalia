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
    </div>
  )
}
