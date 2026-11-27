/**
 * Íconos de trazo (24x24, estilo "lucide") para la carta. Reemplazan a los
 * emojis: se ven igual en todos los celulares, toman el color del texto y
 * no compiten con las fotos de comida. Siempre decorativos (aria-hidden):
 * el texto del botón ya dice qué hace.
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

export const IconoCampana = (p) => (
  <Icono {...p}>
    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
  </Icono>
)

export const IconoRecibo = (p) => (
  <Icono {...p}>
    <path d="M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z" />
    <path d="M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8" />
    <path d="M12 17.5v-11" />
  </Icono>
)

export const IconoMesa = (p) => (
  <Icono {...p}>
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
    <path d="M16 3.13a4 4 0 0 1 0 7.75" />
  </Icono>
)

export const IconoCapas = (p) => (
  <Icono {...p}>
    <path d="m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z" />
    <path d="m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65" />
    <path d="m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65" />
  </Icono>
)

export const IconoMas = (p) => (
  <Icono {...p}>
    <path d="M12 5v14M5 12h14" />
  </Icono>
)

export const IconoMenos = (p) => (
  <Icono {...p}>
    <path d="M5 12h14" />
  </Icono>
)

export const IconoCheck = (p) => (
  <Icono {...p}>
    <path d="M20 6 9 17l-5-5" />
  </Icono>
)

export const IconoRonda = (p) => (
  <Icono {...p}>
    <path d="M3 12a9 9 0 0 1 15.5-6.2L21 8" />
    <path d="M21 3v5h-5" />
    <path d="M21 12a9 9 0 0 1-15.5 6.2L3 16" />
    <path d="M3 21v-5h5" />
  </Icono>
)
