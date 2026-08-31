/**
 * Monograma inline (SVG) inspirado en la paleta del logo real de Mezzalia
 * (circulo terracota/dorado). NO es el logo ilustrado real -- ese vive hoy
 * solo como una foto de un letrero de mesa, no como un archivo vectorial o
 * PNG limpio que se pueda incrustar. En cuanto tengan el archivo real
 * (SVG o PNG con fondo transparente), este componente se reemplaza por un
 * <img> apuntando a ese archivo -- avisenme cuando lo tengan.
 */
export default function LogoMark({ className = 'w-8 h-8' }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden="true">
      <defs>
        <linearGradient id="mezzaliaGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#c1502e" />
          <stop offset="100%" stopColor="#d4a24c" />
        </linearGradient>
      </defs>
      <circle cx="32" cy="32" r="29" fill="none" stroke="url(#mezzaliaGrad)" strokeWidth="2.5" />
      <text
        x="32" y="41" textAnchor="middle"
        fontFamily="'Playfair Display', serif" fontWeight="700" fontSize="24"
        fill="url(#mezzaliaGrad)"
      >
        MZ
      </text>
    </svg>
  )
}
