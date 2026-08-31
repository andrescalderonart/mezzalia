import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import LogoMark from './LogoMark.jsx'

export default function Layout() {
  const { usuario, logout } = useAuth()

  return (
    <div className="min-h-screen flex">
      <aside className="w-64 shrink-0 border-r border-carbon-700 bg-carbon-900/60 flex flex-col">
        <div className="px-5 py-6 flex items-center gap-3">
          <LogoMark className="w-9 h-9" />
          <div>
            <div className="font-display text-lg leading-none text-crema">Mezzalia</div>
            <div className="text-[11px] text-crema/40 tracking-wide">panel del restaurante</div>
          </div>
        </div>

        <nav className="flex-1 px-3 space-y-1">
          <NavLink
            to="/menu"
            className={({ isActive }) =>
              `flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors duration-150 ${
                isActive ? 'bg-carbon-800 text-dorado' : 'text-crema/70 hover:bg-carbon-800 hover:text-crema'
              }`
            }
          >
            Carta
          </NavLink>
        </nav>

        <div className="px-4 py-4 border-t border-carbon-700">
          <div className="text-sm text-crema/80 truncate">{usuario?.username}</div>
          <div className="text-[11px] text-crema/40 mb-3">{usuario?.rol}</div>
          <button onClick={logout} className="btn-secundario w-full text-xs py-1.5">
            Cerrar sesión
          </button>
        </div>
      </aside>

      <main className="flex-1 p-8 overflow-y-auto">
        <div className="animate-fadeInUp">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
