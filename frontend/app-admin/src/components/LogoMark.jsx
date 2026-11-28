/**
 * Monograma provisional de Mezzalia (2026-11-27, rediseño): una "M" sobre un
 * cuadrado redondeado en tinta, con un punto del naranja de la marca. Toma
 * los colores del tema (claro en el panel, oscuro en la cocina). NO es el
 * logo real: cuando exista el archivo (SVG o PNG con fondo transparente),
 * este componente se reemplaza por un <img>.
 */
export default function LogoMark({ className = 'w-8 h-8' }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden="true">
      <rect width="64" height="64" rx="16" fill="rgb(var(--tinta))" />
      <text
        x="31" y="44" textAnchor="middle"
        fontFamily="Geist, system-ui, sans-serif" fontWeight="700" fontSize="32" letterSpacing="-1"
        fill="rgb(var(--c-950))"
      >
        M
      </text>
      <circle cx="49" cy="44" r="4" fill="rgb(var(--acento))" />
    </svg>
  )
}
