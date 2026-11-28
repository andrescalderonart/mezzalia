/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      // 2026-11-27, rediseño: los colores salen de variables CSS (index.css)
      // para que el panel sea CLARO como la carta y la cocina siga OSCURA
      // (clase .tema-oscuro), sin duplicar clases. Nombres históricos:
      // carbon = superficies (950 fondo … 600 borde fuerte), crema = tinta,
      // dorado/terracota = acento naranja de la marca.
      colors: {
        carbon: {
          950: 'rgb(var(--c-950) / <alpha-value>)',
          900: 'rgb(var(--c-900) / <alpha-value>)',
          800: 'rgb(var(--c-800) / <alpha-value>)',
          700: 'rgb(var(--c-700) / <alpha-value>)',
          600: 'rgb(var(--c-600) / <alpha-value>)',
        },
        crema: 'rgb(var(--tinta) / <alpha-value>)',
        terracota: {
          DEFAULT: 'rgb(var(--acento) / <alpha-value>)',
          light: 'rgb(var(--acento) / <alpha-value>)',
          dark: 'rgb(var(--acento) / <alpha-value>)',
        },
        dorado: {
          DEFAULT: 'rgb(var(--acento) / <alpha-value>)',
          light: 'rgb(var(--acento) / <alpha-value>)',
        },
        sin: {
          DEFAULT: 'rgb(var(--peligro) / <alpha-value>)',
          fondo: 'rgb(var(--peligro) / 0.1)',
        },
        peligro: 'rgb(var(--peligro) / <alpha-value>)',
        exito: 'rgb(var(--exito) / <alpha-value>)',
        info: 'rgb(var(--info) / <alpha-value>)',
      },
      fontFamily: {
        display: ['Geist', 'system-ui', 'sans-serif'],
        sans: ['Geist', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        glow: '0 1px 2px rgba(0,0,0,0.4)',
      },
      keyframes: {
        fadeInUp: {
          '0%': { opacity: 0, transform: 'translateY(8px)' },
          '100%': { opacity: 1, transform: 'translateY(0)' },
        },
        pulseSoft: {
          '0%, 100%': { opacity: 1 },
          '50%': { opacity: 0.55 },
        },
        shake: {
          '0%, 100%': { transform: 'translateX(0)' },
          '20%, 60%': { transform: 'translateX(-6px)' },
          '40%, 80%': { transform: 'translateX(6px)' },
        },
      },
      animation: {
        fadeInUp: 'fadeInUp 0.35s ease-out both',
        pulseSoft: 'pulseSoft 1.4s ease-in-out infinite',
        shake: 'shake 0.4s ease-in-out',
      },
    },
  },
  plugins: [],
}
