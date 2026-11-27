/**
 * Íconos de trazo (24x24, estilo "lucide") del panel. Toman el color del
 * texto; siempre decorativos (el texto del enlace o botón dice qué hace).
 */
function Icono({ children, className = 'w-[18px] h-[18px]' }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  )
}

export const IconoPedidos = (p) => (
  <Icono {...p}>
    <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2" />
    <rect x="9" y="3" width="6" height="4" rx="1" />
    <path d="M9 12h6M9 16h4" />
  </Icono>
)

export const IconoCocina = (p) => (
  <Icono {...p}>
    <path d="M17 21a1 1 0 0 0 1-1v-5.35c0-.46.32-.85.74-1.03a4 4 0 0 0-2.27-7.57 5 5 0 0 0-8.94 0 4 4 0 0 0-2.27 7.57c.42.18.74.57.74 1.03V20a1 1 0 0 0 1 1Z" />
    <path d="M6 17h12" />
  </Icono>
)

export const IconoCarta = (p) => (
  <Icono {...p}>
    <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H19a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H6.5a1 1 0 0 1 0-5H20" />
    <path d="M9 7h6M9 11h4" />
  </Icono>
)

export const IconoMesas = (p) => (
  <Icono {...p}>
    <rect x="3" y="3" width="7" height="7" rx="1.5" />
    <rect x="14" y="3" width="7" height="7" rx="1.5" />
    <rect x="3" y="14" width="7" height="7" rx="1.5" />
    <path d="M14 14h3v3h-3zM20 14v.01M14 20h.01M17 20h4v-3" />
  </Icono>
)

export const IconoUsuarios = (p) => (
  <Icono {...p}>
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
  </Icono>
)

export const IconoReportes = (p) => (
  <Icono {...p}>
    <path d="M3 3v18h18" />
    <path d="M7 15l4-4 3 3 5-6" />
  </Icono>
)

export const IconoSucursales = (p) => (
  <Icono {...p}>
    <path d="M3 9l1.5-5h15L21 9" />
    <path d="M3 9a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0" />
    <path d="M5 11v10h14V11M10 21v-5h4v5" />
  </Icono>
)

export const IconoRestaurantes = IconoSucursales

export const IconoMas = (p) => (
  <Icono {...p}>
    <circle cx="5" cy="12" r="1" />
    <circle cx="12" cy="12" r="1" />
    <circle cx="19" cy="12" r="1" />
  </Icono>
)

export const IconoSalir = (p) => (
  <Icono {...p}>
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <path d="M16 17l5-5-5-5M21 12H9" />
  </Icono>
)

export const IconoCerrar = (p) => (
  <Icono {...p}>
    <path d="M18 6 6 18M6 6l12 12" />
  </Icono>
)

export const IconoCampana = (p) => (
  <Icono {...p}>
    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
  </Icono>
)

export const IconoCuenta = (p) => (
  <Icono {...p}>
    <path d="M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z" />
    <path d="M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8M12 17.5v-11" />
  </Icono>
)
