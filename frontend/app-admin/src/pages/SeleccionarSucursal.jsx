import { useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth, useApiJson } from '../context/AuthContext.jsx'
import LogoMark from '../components/LogoMark.jsx'

/**
 * RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §3): pantalla obligatoria
 * para un MESERO que todavía no tiene sucursalId en su access token --
 * ver RequiereSucursal.jsx (guardia de ruta) y AuthContext.sucursalNoSeleccionada
 * (reacción al 409 "SUCURSAL_NO_SELECCIONADA" si lo pierde en caliente).
 *
 * A propósito NO vive dentro de <Layout> (sin sidebar/nav): mostrarle a
 * alguien un menú de "Pedidos/Mesas/Carta" ANTES de que pueda usarlos de
 * verdad (todos le van a devolver 409 hasta elegir) es peor UX que una
 * pantalla simple tipo Login, con la que ya comparte el mismo lenguaje
 * visual.
 */
export default function SeleccionarSucursal() {
  const apiJson = useApiJson()
  const { usuario, seleccionarSucursal, logout } = useAuth()
  const navigate = useNavigate()

  const [sucursales, setSucursales] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [seleccionandoId, setSeleccionandoId] = useState(null)

  useEffect(() => {
    // Ver el guard de rol despues del return principal, mas abajo: si esto
    // termina montado para alguien que no es MESERO (solo posible
    // tipeando la URL a mano), no vale la pena ni disparar la llamada de
    // red -- el componente va a redirigir de todos modos.
    if (usuario?.rol !== 'MESERO') return
    let cancelado = false
    ;(async () => {
      setCargando(true)
      setError('')
      try {
        const datos = await apiJson('/api/admin/sesion/sucursales-disponibles')
        if (!cancelado) setSucursales(datos)
      } catch (e) {
        if (!cancelado) setError(e.message)
      } finally {
        if (!cancelado) setCargando(false)
      }
    })()
    return () => {
      cancelado = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function onElegir(sucursal) {
    setSeleccionandoId(sucursal.id)
    setError('')
    try {
      await seleccionarSucursal(sucursal.id)
      // "/" y no una ruta fija -- deja que InicioRedirect decida (siempre
      // /pedidos para MESERO hoy, pero no hay que duplicar esa regla acá).
      navigate('/', { replace: true })
    } catch (e) {
      setError(e.message)
    } finally {
      setSeleccionandoId(null)
    }
  }

  // RF-10 PR4: esta pantalla es exclusiva de MESERO -- AuthService.
  // seleccionarSucursal() NO se lo impide técnicamente a un
  // ADMIN_RESTAURANTE (el backend soporta "ADMIN_RESTAURANTE enfocado en
  // una sucursal", ver el javadoc de MesaService.obtenerMesa), pero esta
  // UI no ofrece ninguna forma de "desenfocar" de vuelta a ver todas sin
  // cerrar sesión -- dejar que un ADMIN llegue acá por error (tipeando la
  // URL a mano, esta ruta no tiene ningún link visible para su rol) lo
  // dejaría atrapado viendo solo una sucursal sin entender por qué. Si el
  // "focus" de ADMIN_RESTAURANTE se vuelve una necesidad real, eso necesita
  // su propia UI con un botón explícito de "ver todas", no reusar esta
  // pantalla pensada para la selección OBLIGATORIA del MESERO. El chequeo
  // va después de todos los hooks a propósito -- nunca antes, para no
  // violar las Rules of Hooks (deben correr siempre, en el mismo orden).
  if (usuario?.rol !== 'MESERO') {
    return <Navigate to="/" replace />
  }

  return (
    <div className="min-h-dvh flex items-center justify-center px-4 py-8">
      <div className="card w-full max-w-sm p-8 animate-fadeInUp">
        <div className="flex flex-col items-center mb-6">
          <LogoMark className="w-12 h-12 mb-3" />
          <h1 className="font-display text-xl text-crema text-center">¿En qué sucursal trabajás hoy?</h1>
          <p className="text-xs text-crema/40 mt-1 text-center">
            Elegí una para ver sus mesas y pedidos. Podés cambiarla la próxima vez que inicies sesión.
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
          <div className="text-sm text-crema/40 text-center py-4">
            Todavía no tenés ninguna sucursal asignada. Pedile a quien administra el restaurante que te
            asigne una desde Usuarios.
          </div>
        ) : (
          <div className="space-y-2">
            {sucursales.map((sucursal) => (
              <button
                key={sucursal.id}
                onClick={() => onElegir(sucursal)}
                disabled={seleccionandoId !== null}
                className="btn-secundario w-full text-left disabled:opacity-60"
              >
                {seleccionandoId === sucursal.id ? 'Entrando…' : sucursal.nombre}
              </button>
            ))}
          </div>
        )}

        <button
          onClick={logout}
          className="text-xs text-crema/40 hover:text-crema/70 transition-colors duration-150 mt-6 w-full text-center"
        >
          Cerrar sesión
        </button>
      </div>
    </div>
  )
}
