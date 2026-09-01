import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import LogoMark from '../components/LogoMark.jsx'

export default function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)
  const [sacudir, setSacudir] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
    setCargando(true)
    try {
      await login(username, password)
      navigate('/menu', { replace: true })
    } catch (err) {
      setError(err.message || 'No se pudo iniciar sesión')
      setSacudir(true)
      setTimeout(() => setSacudir(false), 400)
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="min-h-dvh flex items-center justify-center px-4 py-8">
      <div className={`card w-full max-w-sm p-8 animate-fadeInUp ${sacudir ? 'animate-shake' : ''}`}>
        <div className="flex flex-col items-center mb-8">
          <LogoMark className="w-14 h-14 mb-3" />
          <h1 className="font-display text-2xl text-crema">Mezzalia</h1>
          <p className="text-xs text-crema/40 mt-1">Panel del restaurante</p>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label className="label-carbon" htmlFor="username">Usuario</label>
            <input
              id="username"
              className="input-carbon"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoFocus
              autoComplete="username"
            />
          </div>
          <div>
            <label className="label-carbon" htmlFor="password">Contraseña</label>
            <input
              id="password"
              type="password"
              className="input-carbon"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </div>

          {error && (
            <div className="text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2 animate-fadeInUp">
              {error}
            </div>
          )}

          <button type="submit" disabled={cargando} className="btn-primario w-full">
            {cargando ? 'Ingresando…' : 'Ingresar'}
          </button>
        </form>
      </div>
    </div>
  )
}
