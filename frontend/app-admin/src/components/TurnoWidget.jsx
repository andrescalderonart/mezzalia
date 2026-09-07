import { useEffect, useState } from 'react'
import { useApiJson } from '../context/AuthContext.jsx'

/**
 * Pedido de Andrés (2026-09-07): la única forma de saber cuánto tiempo
 * trabajado por día lleva alguien es que la propia persona marque
 * entrada/salida -- ver turno.servicio.TurnoService en el backend para
 * por qué (login/refresh de JWT y la asignación mesero<->sucursal no
 * sirven de proxy confiable). Este widget vive en el sidebar (ver
 * Layout.jsx) para que sea imposible de no ver mientras se está usando la
 * app, sin ocupar una pantalla propia -- se usa una sola vez al llegar y
 * una sola vez al irse.
 *
 * No se muestra para SUPER_ADMIN (ver Layout.jsx): esa cuenta no tiene
 * tenant ni turnos que marcar, el backend rechazaría la llamada con
 * SinTenantEnContextoException.
 */
export default function TurnoWidget() {
  const apiJson = useApiJson()
  const [estado, setEstado] = useState(null)
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    let vigente = true
    apiJson('/api/admin/sesion/turno')
      .then((datos) => {
        if (vigente) setEstado(datos)
      })
      .catch(() => {
        // Si esto falla al cargar el sidebar, mejor no mostrar nada que
        // mostrar un error permanente en un widget que se ve en toda la
        // app -- el usuario puede seguir usando el resto del panel igual.
      })
    return () => {
      vigente = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function onToggle() {
    setEnviando(true)
    setError('')
    try {
      const path = estado?.enTurno ? '/api/admin/sesion/turno/salida' : '/api/admin/sesion/turno/entrada'
      const datos = await apiJson(path, { method: 'POST' })
      setEstado(datos)
    } catch (e) {
      setError(e.message)
    } finally {
      setEnviando(false)
    }
  }

  if (estado === null) {
    return null
  }

  const horaInicio = estado.enTurno
    ? new Date(estado.desde).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
    : null

  return (
    <div className="px-4 py-3 border-t border-carbon-700">
      <div className="flex items-center justify-between gap-2 mb-2">
        <span className="text-[11px] text-crema/40">{estado.enTurno ? `En turno desde ${horaInicio}` : 'Fuera de turno'}</span>
        <span className={`w-2 h-2 rounded-full ${estado.enTurno ? 'bg-green-400' : 'bg-crema/20'}`} aria-hidden="true" />
      </div>
      {error && <div className="text-[11px] text-red-300 mb-2">{error}</div>}
      <button
        onClick={onToggle}
        disabled={enviando}
        className={estado.enTurno ? 'btn-secundario w-full text-xs py-2' : 'btn-primario w-full text-xs py-2'}
      >
        {enviando ? 'Guardando…' : estado.enTurno ? 'Marcar salida' : 'Marcar entrada'}
      </button>
    </div>
  )
}
