import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import LogoMark from './LogoMark.jsx'
import TurnoWidget from './TurnoWidget.jsx'
import {
  IconoCarta,
  IconoCerrar,
  IconoCocina,
  IconoMas,
  IconoMesas,
  IconoPedidos,
  IconoReportes,
  IconoRestaurantes,
  IconoSalir,
  IconoSucursales,
  IconoUsuarios,
} from './iconos.jsx'

const NOMBRE_ROL = {
  ADMIN_RESTAURANTE: 'Administrador',
  MESERO: 'Mesero',
  SUPER_ADMIN: 'Equipo Mezzalia',
}

/**
 * Enlaces del panel según el rol. `principal` = va en la barra de pestañas
 * del celular (lo que un mesero toca durante el turno); el resto queda en
 * "Más". El gating real de cada ruta está en RutaConRol.jsx: esto solo
 * decide qué se muestra.
 */
function enlacesPara(rol) {
  if (rol === 'SUPER_ADMIN') {
    return [{ to: '/tenants', texto: 'Restaurantes', Icono: IconoRestaurantes, principal: true }]
  }
  const enlaces = [
    { to: '/pedidos', texto: 'Pedidos', Icono: IconoPedidos, principal: true },
    { to: '/cocina', texto: 'Cocina', Icono: IconoCocina, principal: true },
    { to: '/menu', texto: 'Carta', Icono: IconoCarta, principal: true },
    { to: '/mesas', texto: 'Mesas', Icono: IconoMesas, principal: true },
  ]
  if (rol === 'ADMIN_RESTAURANTE') {
    enlaces.push(
      { to: '/reportes', texto: 'Qué quitan', Icono: IconoReportes },
      { to: '/usuarios', texto: 'Usuarios', Icono: IconoUsuarios },
      { to: '/sucursales', texto: 'Sucursales', Icono: IconoSucursales },
    )
  }
  return enlaces
}

export default function Layout() {
  const { usuario, logout } = useAuth()
  const location = useLocation()
  const [masAbierto, setMasAbierto] = useState(false)
  const enlaces = enlacesPara(usuario?.rol)
  const principales = enlaces.filter((e) => e.principal)

  // Cierra la hoja "Más" al navegar.
  useEffect(() => {
    setMasAbierto(false)
  }, [location.pathname])

  // Sin scroll del fondo mientras la hoja está abierta (iOS rebota si no).
  useEffect(() => {
    document.body.style.overflow = masAbierto ? 'hidden' : ''
    return () => {
      document.body.style.overflow = ''
    }
  }, [masAbierto])

  return (
    <div className="min-h-dvh md:flex">
      {/* Escritorio: barra lateral fija y clara, íconos + texto. */}
      <aside className="hidden md:flex print:hidden sticky top-0 h-dvh w-60 shrink-0 flex-col border-r border-carbon-700 bg-carbon-900">
        <div className="px-4 h-16 flex items-center gap-2.5">
          <LogoMark className="w-8 h-8" />
          <span className="text-[15px] font-semibold tracking-tight text-crema">Mezzalia</span>
        </div>

        <nav className="flex-1 px-3 pt-2 space-y-0.5" aria-label="Secciones del panel">
          {enlaces.map((e) => (
            <EnlaceLateral key={e.to} {...e} />
          ))}
        </nav>

        {usuario?.rol !== 'SUPER_ADMIN' && <TurnoWidget />}
        <Cuenta usuario={usuario} onSalir={logout} />
      </aside>

      {/* Celular: barra superior mínima. */}
      <header
        className="md:hidden print:hidden sticky top-0 z-30 flex items-center gap-2.5 px-4 h-14 border-b border-carbon-700 bg-carbon-900/85 backdrop-blur-xl"
        style={{ paddingTop: 'env(safe-area-inset-top)', boxSizing: 'content-box' }}
      >
        <LogoMark className="w-7 h-7" />
        <span className="text-[15px] font-semibold tracking-tight text-crema">Mezzalia</span>
      </header>

      {/* min-w-0: sin esto un hijo ancho fuerza scroll horizontal de toda
          la página en celular (clásico bug de flexbox). pb extra en celular
          para que la barra de pestañas no tape el final del contenido. */}
      <main className="flex-1 min-w-0 px-4 pt-5 pb-28 sm:px-6 md:pb-10 lg:px-10 lg:pt-8 print:p-0">
        <div className="max-w-6xl mx-auto">
          <Outlet />
        </div>
      </main>

      {/* Celular: barra de pestañas inferior, como una app nativa. */}
      <nav
        className="md:hidden print:hidden fixed bottom-0 inset-x-0 z-30 border-t border-carbon-700 bg-carbon-900/90 backdrop-blur-xl"
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
        aria-label="Secciones del panel"
      >
        <div className="flex">
          {principales.map((e) => (
            <EnlacePestana key={e.to} {...e} />
          ))}
          {(enlaces.length > principales.length || usuario?.rol !== 'SUPER_ADMIN') && (
            <button
              type="button"
              onClick={() => setMasAbierto(true)}
              aria-haspopup="dialog"
              className="flex-1 flex flex-col items-center gap-1 pt-2 pb-1.5 text-[11px] font-medium text-crema/50"
            >
              <IconoMas className="w-[22px] h-[22px]" />
              Más
            </button>
          )}
        </div>
      </nav>

      {masAbierto && (
        <div className="md:hidden fixed inset-0 z-40 flex items-end" role="dialog" aria-modal="true" aria-label="Más opciones">
          <div className="absolute inset-0 bg-black/40" onClick={() => setMasAbierto(false)} aria-hidden="true" />
          <div
            className="relative w-full rounded-t-3xl bg-carbon-900 animate-fadeInUp overscroll-contain"
            style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
          >
            <div className="flex items-center justify-between px-5 pt-4 pb-2">
              <h2 className="text-base font-semibold text-crema">Más</h2>
              <button
                type="button"
                onClick={() => setMasAbierto(false)}
                aria-label="Cerrar"
                className="w-9 h-9 rounded-full bg-carbon-800 flex items-center justify-center text-crema"
              >
                <IconoCerrar className="w-4 h-4" />
              </button>
            </div>
            <nav className="px-3 space-y-0.5">
              {enlaces
                .filter((e) => !e.principal)
                .map((e) => (
                  <EnlaceLateral key={e.to} {...e} />
                ))}
            </nav>
            {usuario?.rol !== 'SUPER_ADMIN' && <TurnoWidget />}
            <Cuenta usuario={usuario} onSalir={logout} />
          </div>
        </div>
      )}
    </div>
  )
}

function EnlaceLateral({ to, texto, Icono }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `flex items-center gap-3 rounded-lg px-3 h-10 text-sm font-medium transition-colors duration-150 ${
          isActive ? 'bg-carbon-800 text-crema' : 'text-crema/60 hover:bg-carbon-800 hover:text-crema'
        }`
      }
    >
      <Icono />
      {texto}
    </NavLink>
  )
}

function EnlacePestana({ to, texto, Icono }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `flex-1 flex flex-col items-center gap-1 pt-2 pb-1.5 text-[11px] font-medium transition-colors ${
          isActive ? 'text-crema' : 'text-crema/50'
        }`
      }
    >
      <Icono className="w-[22px] h-[22px]" />
      {texto}
    </NavLink>
  )
}

function Cuenta({ usuario, onSalir }) {
  const inicial = (usuario?.username || '?').charAt(0).toUpperCase()
  return (
    <div className="px-3 py-3 border-t border-carbon-700 flex items-center gap-3">
      <span className="w-8 h-8 shrink-0 rounded-full bg-carbon-800 flex items-center justify-center text-sm font-semibold text-crema">
        {inicial}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium text-crema truncate">{usuario?.username}</span>
        <span className="block text-xs text-crema/50">{NOMBRE_ROL[usuario?.rol] || usuario?.rol}</span>
      </span>
      <button
        type="button"
        onClick={onSalir}
        aria-label="Cerrar sesión"
        title="Cerrar sesión"
        className="w-9 h-9 shrink-0 rounded-lg flex items-center justify-center text-crema/50 hover:bg-carbon-800 hover:text-crema transition-colors"
      >
        <IconoSalir />
      </button>
    </div>
  )
}
