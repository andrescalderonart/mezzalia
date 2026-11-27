import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth, useApiJson } from '../context/AuthContext.jsx'
import LogoMark from '../components/LogoMark.jsx'

/**
 * RF-10 PR4: pantalla que cierra el flujo que el backend exige (ver
 * TokensRespuesta.requiereSeleccionSucursal, SesionController y el
 * gate en RequiereSucursal.jsx) -- sin esta pantalla, un MESERO nunca
 * tiene forma de completar POST /api/admin/sesion/sucursal y queda
 * bloqueado en un 409 permanente la primera vez que entra a Mesas/Pedidos.
 *
 * Full-screen, fuera de <Layout> (como Login) -- todavía no tiene sentido
 * mostrarle el sidebar completo a alguien que ni siquiera eligió con qué
 * sucursal va a trabajar.
 *
 * También accesible para ADMIN_RESTAURANTE (no solo MESERO): para ese rol
 * la selección es una conveniencia opcional para acotar su propia vista
 * (ver AuthService.misSucursalesDisponibles), nunca obligatoria -- por eso
 * el botón "Ver todas las sucursales" de abajo solo aparece para ese rol.
 */
export default function SeleccionarSucursalPage() {
  const { usuario, seleccionarSucursal, logout } = useAuth()
  const apiJson = useApiJson()
  const navigate = useNavigate()
  const location = useLocation()

  const [sucursales, setSucursales] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [eligiendoId, setEligiendoId] = useState(null)

  const destino = location.state?.desde || '/pedidos'

  useEffect(() => {
    let vigente = true
    setCargando(true)
    apiJson('/api/admin/sesion/sucursales-disponibles')
      .then((datos) => {
        if (vigente) setSucursales(datos)
      })
      .catch((e) => {
        if (vigente) setError(e.message)
      })
      .finally(() => {
        if (vigente) setCargando(false)
      })
    return () => {
      vigente = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function onElegir(sucursal) {
    setEligiendoId(sucursal.id)
    setError('')
    try {
      await seleccionarSucursal(sucursal.id)
      navigate(destino, { replace: true })
    } catch (e) {
      setError(e.message)
    } finally {
      setEligiendoId(null)
    }
  }

  const esMesero = usuario?.rol === 'MESERO'

  return (
    <div className="min-h-dvh flex items-center justify-center px-4 py-8">
      <div className="card w-full max-w-sm p-8 animate-fadeInUp">
        <div className="flex flex-col items-center mb-6">
          <LogoMark className="w-14 h-14 mb-3" />
          <h1 className="font-display font-semibold text-2xl text-crema">¿En qué sucursal trabajas?</h1>
          <p className="text-xs text-crema/40 mt-1 text-center">
            {esMesero
              ? 'Elige una sucursal para ver y atender sus mesas y pedidos.'
              : 'Opcional: elige una sucursal para enfocar tu vista, o mira todas.'}
          </p>
        </div>

        {error && (
          <div className="mb-4 text-sm text-red-300 bg-red-950/50 border border-red-900/60 rounded-lg px-3 py-2 animate-fadeInUp">
            {error}
          </div>
        )}

        {cargando ? (
          <div className="space-y-2">
            {[1, 2].map((n) => (
              <div key={n} className="h-12 rounded-xl bg-carbon-800 animate-pulseSoft" />
            ))}
          </div>
        ) : sucursales.length === 0 ? (
          <div className="text-sm text-crema/40 text-center py-6">
            {esMesero
              ? 'Todavía no tienes ninguna sucursal asignada. Pídele a un administrador que te asigne una para poder trabajar.'
              : 'Este restaurante todavía no tiene sucursales creadas.'}
          </div>
        ) : (
          <div className="space-y-2 mb-4">
            {sucursales.map((sucursal) => (
              <button
                key={sucursal.id}
                onClick={() => onElegir(sucursal)}
                disabled={eligiendoId !== null}
                className="btn-secundario w-full text-left px-4 py-3 flex flex-col"
              >
                <span className="text-sm text-crema">{sucursal.nombre}</span>
                {sucursal.direccion && <span className="text-[11px] text-crema/40">{sucursal.direccion}</span>}
                {eligiendoId === sucursal.id && <span className="text-[11px] text-dorado mt-1">Entrando…</span>}
              </button>
            ))}
          </div>
        )}

        {!esMesero && (
          <button onClick={() => navigate(destino, { replace: true })} className="btn-secundario w-full text-xs py-2 mb-2">
            Ver todas las sucursales
          </button>
        )}

        <button onClick={logout} className="text-xs text-crema/40 hover:text-crema/70 transition-colors duration-150 w-full text-center py-1">
          Cerrar sesión
        </button>
      </div>
    </div>
  )
}
