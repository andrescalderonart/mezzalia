import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import LogoMark from './LogoMark.jsx'

export default function Layout() {
  const { usuario, logout } = useAuth()
  const location = useLocation()
  const [menuAbierto, setMenuAbierto] = useState(false)

  // Cierra el drawer al navegar -- si no, queda tapando la pantalla
  // después de tocar un link en móvil.
  useEffect(() => {
    setMenuAbierto(false)
  }, [location.pathname])

  // Bloquea el scroll del body detrás del overlay en móvil: sin esto,
  // iOS Safari deja hacer "rubber-band" scroll del fondo mientras el
  // drawer está abierto y se siente roto.
  useEffect(() => {
    document.body.style.overflow = menuAbierto ? 'hidden' : ''
    return () => {
      document.body.style.overflow = ''
    }
  }, [menuAbierto])

  return (
    <div className="min-h-dvh flex flex-col md:flex-row">
      {/* Barra superior solo en móvil/tablet. El sidebar fijo de 256px del
          diseño de escritorio no cabe en una pantalla de 375-430px sin
          dejar el contenido real en ~110px de ancho, así que aquí se
          colapsa a un drawer con hamburguesa. */}
      <header
        className="md:hidden print:hidden sticky top-0 z-30 flex items-center justify-between px-4 py-3
                   border-b border-carbon-700 bg-carbon-900/90 backdrop-blur-sm"
        style={{ paddingTop: 'calc(0.75rem + env(safe-area-inset-top))' }}
      >
        <div className="flex items-center gap-2.5">
          <LogoMark className="w-7 h-7" />
          <span className="font-display text-base text-crema">Mezzalia</span>
        </div>
        <button
          onClick={() => setMenuAbierto(true)}
          aria-label="Abrir menú"
          className="flex items-center justify-center w-11 h-11 -mr-2 rounded-lg text-crema/80
                     hover:bg-carbon-800 active:bg-carbon-800 transition-colors duration-150"
        >
          <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>
      </header>

      {/* Overlay oscuro detrás del drawer -- tocar afuera cierra */}
      {menuAbierto && (
        <div
          className="fixed inset-0 bg-black/60 z-40 md:hidden"
          onClick={() => setMenuAbierto(false)}
          aria-hidden="true"
        />
      )}

      <aside
        className={`fixed md:static print:hidden inset-y-0 left-0 z-50 w-72 max-w-[85vw] md:w-64 md:max-w-none shrink-0
                    border-r border-carbon-700 bg-carbon-900 md:bg-carbon-900/60 flex flex-col
                    transform transition-transform duration-200 ease-out
                    ${menuAbierto ? 'translate-x-0' : '-translate-x-full'} md:translate-x-0`}
        style={{ paddingTop: 'env(safe-area-inset-top)', paddingBottom: 'env(safe-area-inset-bottom)' }}
      >
        <div className="px-5 py-6 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <LogoMark className="w-9 h-9" />
            <div>
              <div className="font-display text-lg leading-none text-crema">Mezzalia</div>
              <div className="text-[11px] text-crema/40 tracking-wide">panel del restaurante</div>
            </div>
          </div>
          <button
            onClick={() => setMenuAbierto(false)}
            aria-label="Cerrar menú"
            className="md:hidden flex items-center justify-center w-9 h-9 rounded-lg text-crema/60 hover:bg-carbon-800 transition-colors duration-150"
          >
            <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <nav className="flex-1 px-3 space-y-1">
          {/* SUPER_ADMIN no tiene tenant -- estos 3 links le devolverian un
              error de backend (SinTenantEnContextoException), asi que ni
              se muestran para ese rol. Ver RutaConRol.jsx para el gating
              real (esto es solo ocultar el link, no la unica defensa). */}
          {usuario?.rol !== 'SUPER_ADMIN' && (
            <>
              <NavLink
                to="/pedidos"
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm transition-colors duration-150 ${
                    isActive ? 'bg-carbon-800 text-dorado' : 'text-crema/70 hover:bg-carbon-800 hover:text-crema'
                  }`
                }
              >
                Pedidos
              </NavLink>
              <NavLink
                to="/menu"
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm transition-colors duration-150 ${
                    isActive ? 'bg-carbon-800 text-dorado' : 'text-crema/70 hover:bg-carbon-800 hover:text-crema'
                  }`
                }
              >
                Carta
              </NavLink>
              <NavLink
                to="/mesas"
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm transition-colors duration-150 ${
                    isActive ? 'bg-carbon-800 text-dorado' : 'text-crema/70 hover:bg-carbon-800 hover:text-crema'
                  }`
                }
              >
                Mesas
              </NavLink>
            </>
          )}
          {usuario?.rol === 'SUPER_ADMIN' && (
            <NavLink
              to="/tenants"
              className={({ isActive }) =>
                `flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm transition-colors duration-150 ${
                  isActive ? 'bg-carbon-800 text-dorado' : 'text-crema/70 hover:bg-carbon-800 hover:text-crema'
                }`
              }
            >
              Restaurantes
            </NavLink>
          )}
        </nav>

        <div className="px-4 py-4 border-t border-carbon-700">
          <div className="text-sm text-crema/80 truncate">{usuario?.username}</div>
          <div className="text-[11px] text-crema/40 mb-3">{usuario?.rol}</div>
          <button onClick={logout} className="btn-secundario w-full text-xs py-2">
            Cerrar sesión
          </button>
        </div>
      </aside>

      {/* min-w-0 es obligatorio aquí: sin esto, un hijo con contenido que
          no puede envolver (grids anchos, texto largo) fuerza overflow
          horizontal de TODA la página en viewports angostos, en vez de
          recortarse dentro de su columna flex -- clásico bug de flexbox
          que "funciona en desktop" y se rompe en celular. */}
      <main className="flex-1 min-w-0 p-4 sm:p-6 lg:p-8 overflow-y-auto print:p-0 print:overflow-visible">
        <div className="animate-fadeInUp">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
